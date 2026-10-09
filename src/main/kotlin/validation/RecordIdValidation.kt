package validation

private val asciiDigits = Regex("[0-9]+")

/** Match the unsigned 64-bit range used by the record comparator, not a fixed server ID length. */
fun recordIdValidationError(id: String): String? = when {
    id.isBlank() -> "Record ID is empty"
    id.length > 20 || !asciiDigits.matches(id) || id.toULongOrNull() == null ->
        "Record ID must contain 1 to 20 ASCII digits within the unsigned 64-bit range"
    else -> null
}

/** Item IDs remain strings, independent of the current catalog and Int range. */
fun itemIdValidationError(id: String): String? = when {
    id.isBlank() -> "Item ID is empty"
    !asciiDigits.matches(id) -> "Item ID must contain ASCII digits only"
    else -> null
}
