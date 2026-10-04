package model

data class ValidationIssue(
    val severity: Severity,
    val message: String,
    val recordId: String? = null,
)

data class ValidationReport(
    val issues: List<ValidationIssue>,
) {
    val hasErrors: Boolean get() = issues.any { it.severity == Severity.ERROR }
    val errorCount: Int get() = issues.count { it.severity == Severity.ERROR }
    val warnCount: Int get() = issues.count { it.severity == Severity.WARN }
}

