package logger

import model.Severity

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import storage.IOConfiguration
import java.io.BufferedWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.file.Files
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

data class LogBody(
    val level: Severity,
    val message: String,
    val throwable: Throwable? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/** Console-only debugging, independent of the file logger. */
fun debug(message: String, throwable: Throwable? = null) {
    println("[Debug ONLY!!!] $message")
    if (throwable != null) {println("[Debug ONLY!!!] error: $message") }
}

class LogWriter private constructor() {
    companion object {
        val instance: LogWriter by lazy { LogWriter() }
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val logFile = IOConfiguration.default_configPath.resolve("app.log")

    private val logChannel = Channel<LogBody>(
        capacity = 12800,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val writeHandler = scope.launch {
        try {
            Files.createDirectories(logFile.parent)
            Files.newBufferedWriter(logFile, Charsets.UTF_8, CREATE, APPEND).use { writer ->
                for (message in logChannel) {
                    writeToLogFile(message, writer)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            System.err.println("Log writer failed: ${e.message}")
            e.printStackTrace(System.err)
        } finally {
            logChannel.cancel()
            scope.cancel()
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread({
            runBlocking { closeAndJoin() }
        }, "log-writer-shutdown"))
    }

    /** Attempts to enqueue; on failure reports to stderr and returns false. */
    fun tryLog(logBody: LogBody): Boolean {
        val accepted = logChannel.trySend(logBody).isSuccess
        if (!accepted) {
            System.err.println("Log queue full or closed: [${logBody.timestamp}] [${logBody.level}] ${logBody.message}")
            logBody.throwable?.printStackTrace(System.err)
        }
        return accepted
    }

    @Deprecated("This sends a log; use tryLog instead", ReplaceWith("tryLog(logBody)"))
    fun isFull(logBody: LogBody): Boolean = tryLog(logBody)

    private fun writeToLogFile(logBody: LogBody, writer: BufferedWriter) {
        val timestamp = logBody.timestamp.format(formatter)
        val line = buildString {
            append("[$timestamp] [${logBody.level}] ${logBody.message}")
            if (logBody.throwable != null) {
                append('\n')
                val sw = StringWriter()
                logBody.throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString())
            }
            append('\n')
        }
        print(line)
        writer.write(line)
        // Keep logs promptly visible while reusing the same open writer.
        writer.flush()
    }


    /** Waits for draining and writer closure. Stop producers before calling. */
    suspend fun closeAndJoin() {
        logChannel.close()
        writeHandler.join()
    }
}
