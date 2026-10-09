package validation

import model.ValidationIssue
import model.ValidationReport
import model.Severity

import model.GachaRecord
import utilities.AppConstants
import utilities.records.compareChronologically

object DataValidator {

    fun validate(records: List<GachaRecord>, uid: String, isKnownItem: (String) -> Boolean): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        // validate UID, returning error
        uidValidationError(uid)?.let { message ->
            issues.add(ValidationIssue(Severity.ERROR, message))
        }
        if (records.isEmpty()) {
            issues.add(ValidationIssue(Severity.ERROR, "No gacha records found"))
        }

        val ids = mutableSetOf<String>()
        records.forEach { record ->
            // validate record and remove dups
            val recordIdError = recordIdValidationError(record.recordID)
            if (recordIdError != null) {
                issues.add(ValidationIssue(Severity.ERROR, recordIdError, record.recordID))
            } else if (!ids.add(record.recordID)) {
                issues.add(ValidationIssue(Severity.ERROR, "Duplicate record id: ${record.recordID}", record.recordID))
            }

            // time and rank type validation, must return Severity.ERROR as it should NOT be included.
            if (!record.time.isValidTime()) {
                issues.add(ValidationIssue(Severity.ERROR, "Invalid date/time: ${record.time}", record.recordID))
            }
            if (record.rankType !in 3..5){
                issues.add(ValidationIssue(Severity.ERROR, "Invalid rank type: ${record.rankType}", record.recordID))
            }

            if (record.gachaType.isBlank() || !AppConstants.BannerCodeList.contains(record.gachaType)) {
                issues.add(ValidationIssue(Severity.ERROR, "Invalid gacha type ${record.gachaType}", record.recordID))
            }

            // item ID validation. Note that it can be WARN!! because json may not be up-to-date
            val itemIdError = itemIdValidationError(record.itemID)
            if (itemIdError != null) {
                issues.add(ValidationIssue(Severity.ERROR, itemIdError, record.recordID))
            } else if (!isKnownItem(record.itemID)) {
                issues.add(ValidationIssue(Severity.WARN, "Unknown item ID: ${record.itemID}", record.recordID))
            }

            val expectedUigfType = if (record.gachaType == AppConstants.CHARACTER_EVENT_BANNER2)
                AppConstants.CHARACTER_EVENT_BANNER else record.gachaType
            if (record.uigfGachaType != expectedUigfType) {
                issues.add(ValidationIssue(Severity.ERROR,
                    "Incompatible UIGF gacha type: ${record.uigfGachaType}; expected $expectedUigfType for ${record.gachaType}",
                    record.recordID))
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
