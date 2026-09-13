package validation

import model.GachaRecord
import model.compareChronologically

data class ValidationIssue(
    val severity: Severity,
    val message: String,
    val recordId: String? = null,
)

enum class Severity { INFO, WARN, ERROR }

data class ValidationReport(
    val issues: List<ValidationIssue>,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
    val errorCount: Int get() = issues.count { it.severity == Severity.ERROR }
    val warnCount: Int get() = issues.count { it.severity == Severity.WARN }
}

object DataValidator {

    fun validate(records: List<GachaRecord>, uid: String): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        if (uid.isBlank()) {
            issues.add(ValidationIssue(Severity.ERROR, "UID is empty"))
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
            if (!record.time.matches("""\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}""".toRegex())) {
                issues.add(ValidationIssue(Severity.WARN, "Invalid time format: ${record.time}", record.recordID))
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
