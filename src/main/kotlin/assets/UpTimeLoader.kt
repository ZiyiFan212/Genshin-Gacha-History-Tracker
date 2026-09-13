package assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utilities.AppLogger
import java.time.Instant

object UpTimeLoader {

    private const val UP_TIME_PATH = "/gacha-assets/upTime.json"

    /**
     * Duration stored as "startMs-endMs" string, or null if not set.
     * startMs/endMs are epoch milliseconds (13-digit).
     */
    @Serializable
    data class UpTimeEntry(val duration: String? = null) {
        fun startMs(): Long? = duration?.split("-")?.getOrNull(0)?.toLongOrNull()
        fun endMs(): Long? = duration?.split("-")?.getOrNull(1)?.toLongOrNull()
        fun startInstant(): Instant? = startMs()?.let { Instant.ofEpochMilli(it) }
        fun endInstant(): Instant? = endMs()?.let { Instant.ofEpochMilli(it) }
        fun contains(timeMs: Long): Boolean {
            val s = startMs() ?: return false
            val e = endMs() ?: return false
            return timeMs in s..e
        }
    }

    private val json = Json { ignoreUnknownKeys = true }
    private var upTimeMap = emptyMap<String, UpTimeEntry>()
    private var initialized = false

    fun isLoaded(): Boolean = initialized

    fun load(): Result<Unit> = runCatching {
        val text = UpTimeLoader::class.java.classLoader
            .getResourceAsStream(UP_TIME_PATH.trimStart('/'))
            ?.bufferedReader()?.readText()
            ?: throw IllegalStateException("Missing upTime.json")
        upTimeMap = json.decodeFromString<Map<String, UpTimeEntry>>(text)
        initialized = true
    }

    fun getEntry(itemId: String): UpTimeEntry? {
        check(initialized) { "UpTimeLoader has not been loaded" }
        return upTimeMap[itemId]
    }

    /**
     * Returns the UP duration string "startMs-endMs", or null if not set.
     */
    fun getDuration(itemId: String): String? {
        check(initialized) { "UpTimeLoader has not been loaded" }
        return upTimeMap[itemId]?.duration
    }

    /**
     * Checks if the given item was in UP at the given time (epoch ms).
     */
    fun isUpAtTime(itemId: String, timeMs: Long): Boolean {
        return getEntry(itemId)?.contains(timeMs) ?: false
    }

    fun getAllEntries(): Map<String, UpTimeEntry> {
        check(initialized) { "UpTimeLoader has not been loaded" }
        return upTimeMap
    }
}