package validation

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import utilities.SUPPORTED_SERVER_TIMEZONE
import utilities.inferredServerTimezone

/** Validate before discarding import metadata; never reinterpret record time strings. */
fun requireSupportedImportTimezone(metadata: JsonObject, uid: String, field: String, required: Boolean) {
    val offset = if (field in metadata) {
        val value = metadata[field] as? JsonPrimitive
        require(value != null && !value.isString && value.intOrNull != null) {
            "$field must be an integer timezone offset"
        }
        value.intOrNull!!
    } else {
        require(!required) { "Missing required timezone field: $field" }
        inferredServerTimezone(uid)
    }
    require(offset == SUPPORTED_SERVER_TIMEZONE) {
        "Unsupported server timezone: $offset; only UTC+8 records are supported"
    }
}
