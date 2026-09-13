package utilities

import model.GachaRecord
import model.sortedChronologically
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun String?.isDuplicate(uids: List<String>): Boolean {
    if (this == null) return false
    return uids.any { it.equals(this, ignoreCase = true) }
}

fun List<GachaRecord>.mergeWith(localData: List<GachaRecord>): List<GachaRecord> {
    return (this + localData)
        .filter { it.time.isValidTime() }
        .distinctBy { it.dedupKey() }
        .sortedChronologically()
}

private fun GachaRecord.dedupKey(): String {
    return if (recordID.isNotBlank()) {
        recordID
    } else {
        "${gachaType}|${time}|${itemID}|${name}|${rankType}"
    }
}

fun String?.isValidTime(): Boolean {
    val pattern = """\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""".toRegex()
    if (this == null || !this.matches(pattern)) return false

    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    return runCatching { LocalDateTime.parse(this, formatter) }.isSuccess
}