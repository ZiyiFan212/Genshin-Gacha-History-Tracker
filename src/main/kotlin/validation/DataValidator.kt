package validation

import model.ValidationIssue
import model.ValidationReport
import model.Severity

import model.GachaRecord
import utilities.records.compareChronologically
import utilities.records.isValidTime

object DataValidator {

    fun validate(records: List<GachaRecord>, uid: String): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        uidValidationError(uid)?.let { message ->
            issues.add(ValidationIssue(Severity.ERROR, message))
        }
        if (records.isEmpty()) {
            issues.add(ValidationIssue(Severity.ERROR, "No gacha records found"))
        }

        val ids = mutableSetOf<String>()
        records.forEach { record ->
            if (record.recordID.isBlank()) {
                issues.add(ValidationIssue(Severity.WARN, "Record missing id at ${record.time}", null))
            } else if (!ids.add(record.recordID)) {
                issues.add(ValidationIssue(Severity.ERROR, "Duplicate record id: ${record.recordID}", record.recordID))
            }
            if (!record.time.isValidTime()) {
                issues.add(ValidationIssue(Severity.ERROR, "Invalid date/time: ${record.time}", record.recordID))
            }
            if (record.gachaType.isBlank()) {
                issues.add(ValidationIssue(Severity.WARN, "Missing gacha type", record.recordID))
            }
        }

        for (i in 1 until records.size) {
            if (records[i].compareChronologically(records[i - 1]) < 0) {
                val id = records[i].recordID.ifBlank { records[i].time }
                issues.add(ValidationIssue(Severity.WARN, "Records not in chronological order around id $id", records[i].recordID))
                break
            }
        }

        return ValidationReport(issues)
    }

}
