package validation

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

// strict pattern to ensure no faulty time format
private val timePattern = """\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""".toRegex()
private val strictTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)

fun String?.isValidTime(): Boolean {
    if (this == null || !this.matches(timePattern)) return false

    try {
        LocalDateTime.parse(this, strictTimeFormatter)
    } catch (_: DateTimeParseException) {
        return false
    }
    return true
}