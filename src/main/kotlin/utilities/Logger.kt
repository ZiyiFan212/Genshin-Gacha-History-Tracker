package utilities

import storage.IOConfiguration
import java.io.PrintWriter
import java.io.StringWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.appendText

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Output the log to the console and save them to the directory (Path initialized in): [IOConfiguration].
 *
 * Has functions serving for different purpose: debug[debug], warning[warn], information[info], or error[error].
 */
object AppLogger {
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    private val logFile = IOConfiguration.default_configPath.resolve("app.log")

    fun debug(message: String) = log(LogLevel.DEBUG, message)
    fun info(message: String) = log(LogLevel.INFO, message)
    fun warn(message: String) = log(LogLevel.WARN, message)
    fun error(message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, message, throwable)

    private fun log(level: LogLevel, message: String, throwable: Throwable? = null) {
        val timestamp = LocalDateTime.now().format(formatter)
        val line = buildString {// build line
            append("[$timestamp] [${level.name}] $message")
            if (throwable != null) {
                append('\n')
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                append(sw.toString())
            }
            append('\n')
        }
        println(line)
        runCatching {
            logFile.parent.toFile().mkdirs()
            logFile.appendText(line.trimEnd())
        }
    }
}