package utilities.records

import logger.LogBody
import model.Severity
import logger.LogWriter
import model.GachaRecord
import validation.isValidTime

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


private fun GachaRecord.dedupKey(): String {
    return if (recordID.isNotBlank()) {
        recordID
    } else {
        "${gachaType}|${time}|${itemID}|${name}|${rankType}"
    }
}

