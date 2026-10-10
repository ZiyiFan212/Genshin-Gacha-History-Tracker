package utilities

import java.time.ZoneOffset

const val SUPPORTED_SERVER_TIMEZONE = 8
val SERVER_TIME_OFFSET: ZoneOffset = ZoneOffset.ofHours(SUPPORTED_SERVER_TIMEZONE)

/** UIGF fallback only when v3 has no explicit region_time_zone. */
fun inferredServerTimezone(uid: String): Int = when (uid.firstOrNull()) {
    '6' -> -5
    '7' -> 1
    else -> 8
}
