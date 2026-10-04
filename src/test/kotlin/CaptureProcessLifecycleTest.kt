import core.ProxyLifecycle
import core.NodeLocator
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CaptureProcessLifecycleTest {
    @Test
    fun `close asks child to exit before recovery and is idempotent`() {
        var recovered = 0
        val lifecycle = ProxyLifecycle({ recovered++ }, graceMillis = 2_000)
        try {
            val process = lifecycle.start(ProcessBuilder(NodeLocator.resolveNodejsEnvironment(), "-e",
                "process.stdin.on('end', () => process.exit(0)); process.stdin.resume(); console.log('ready')"))
            assertEquals("ready", process.inputStream.bufferedReader().readLine())
            lifecycle.close()
            assertFalse(process.isAlive)
            assertEquals(0, process.exitValue())
            lifecycle.close()
            assertEquals(1, recovered)
        } finally { lifecycle.close() }
    }

    @Test
    fun `cleanup without a started child does not restore proxy settings`() {
        var recovered = 0
        ProxyLifecycle({ recovered++ }).close()
        assertEquals(0, recovered)
    }

    @Test
    fun `unresponsive owned child is stopped before recovery`() {
        var recovered = 0
        val lifecycle = ProxyLifecycle({ recovered++ }, graceMillis = 100)
        val process = lifecycle.start(ProcessBuilder(NodeLocator.resolveNodejsEnvironment(), "-e",
            "setInterval(() => {}, 1000); console.log('ready')"))
        try {
            assertEquals("ready", process.inputStream.bufferedReader().readLine())
            lifecycle.close()
            assertFalse(process.isAlive)
            assertEquals(1, recovered)
        } finally {
            lifecycle.close()
            if (process.isAlive) { process.destroyForcibly(); process.waitFor(2, TimeUnit.SECONDS) }
        }
    }
}
