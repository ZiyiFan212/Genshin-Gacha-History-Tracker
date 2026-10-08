package utilities.records

import logger.LogBody
import model.Severity
import logger.LogWriter
import model.GachaRecord
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

fun List<GachaRecord>.mergeWith(localData: List<GachaRecord>): List<GachaRecord> {
    requireValidRecordTimes(this + localData)
    return (this + localData)
        .distinctBy { it.dedupKey() }
        .sortedChronologically()
}

fun requireValidRecordTimes(records: List<GachaRecord>) {
    val invalid = records.filterNot { it.time.isValidTime() }
    if (invalid.isEmpty()) return
    val message = "Rejected entire batch: ${invalid.size} record(s) have invalid date/time"
    LogWriter.instance.tryLog(LogBody(Severity.ERROR, message))
    throw IllegalArgumentException(message)
}

// strict pattern to ensure no faulty time format
private val timePattern = """\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""".toRegex()
private val strictTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss").withResolverStyle(ResolverStyle.STRICT)

private fun GachaRecord.dedupKey(): String {
    return if (recordID.isNotBlank()) {
        recordID
    } else {
        "${gachaType}|${time}|${itemID}|${name}|${rankType}"
    }
}

fun String?.isValidTime(): Boolean {
    if (this == null || !this.matches(timePattern)) return false

    try {
        LocalDateTime.parse(this, strictTimeFormatter)
    } catch (_: DateTimeParseException) {
        return false
    }
    return true
}
