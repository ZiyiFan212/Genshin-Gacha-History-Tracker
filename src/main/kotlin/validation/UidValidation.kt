package validation

private val uidPattern = Regex("[0-9]{9,10}")

/** Null means valid; otherwise returns the reason without altering the UID. */
fun uidValidationError(uid: String): String? = when {
    uid.isBlank() -> "UID is empty"
    !uidPattern.matches(uid) -> "UID must contain 9 or 10 ASCII digits"
    else -> null
}
