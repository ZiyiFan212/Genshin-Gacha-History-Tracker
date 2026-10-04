package core

import java.util.concurrent.TimeUnit

/** The normal exit path and JVM shutdown share one serialized cleanup operation. */
internal class ProxyLifecycle(
    private val recover: () -> Unit,
    private val graceMillis: Long = 8_000,
) : AutoCloseable {
    private var process: Process? = null
    private var closed = false
    private val hook = Thread({ close() }, "capture-session-shutdown")

    init { Runtime.getRuntime().addShutdownHook(hook) }

    @Synchronized
    fun start(builder: ProcessBuilder): Process {
        check(!closed && process == null) { "Capture session already closed or started" }
        return builder.start().also { process = it }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        val interrupted = Thread.interrupted()
        try {
            process?.let { child ->
                // EOF asks Node to restore its own settings. It also detects unexpected parent death.
                runCatching { child.outputStream.close() }
                if (!child.waitFor(graceMillis, TimeUnit.MILLISECONDS)) {
                    val descendants = child.descendants().use { it.toList() }
                    child.destroyForcibly()
                    descendants.forEach { it.destroyForcibly() }
                    check(child.waitFor(10, TimeUnit.SECONDS)) { "Capture process did not stop; snapshot retained" }
                    descendants.forEach { it.onExit().get(10, TimeUnit.SECONDS) }
                }
                recover()
            }
        } finally {
            // Removing a hook during JVM shutdown is prohibited; the hook itself remains idempotent.
            runCatching { Runtime.getRuntime().removeShutdownHook(hook) }
            if (interrupted) Thread.currentThread().interrupt()
        }
    }
}
