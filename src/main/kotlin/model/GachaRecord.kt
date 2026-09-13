package model

import kotlinx.serialization.*
import kotlinx.serialization.json.Json

@Serializable
data class GachaRecord(
    @SerialName("gacha_type") val gachaType: String,
    val time: String,
    val name: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("item_id") val itemID: String,
    @SerialName("id") val recordID: String,
    @SerialName("rank_type") val rankType: Int
)

@Serializable
data class Info(
    val uid: String,
)

@Serializable
data class UIGFWrapper(
    val info: Info,
    @SerialName("list") val list: List<GachaRecord> = emptyList(),
    @SerialName("records") val records: List<GachaRecord> = emptyList(),
)

val customizeJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

fun parseJson(jsonString: String): Result<Pair<String, List<GachaRecord>>> = runCatching {
    val wrapper = customizeJson.decodeFromString<UIGFWrapper>(jsonString)
    val rawRecords = wrapper.list.ifEmpty { wrapper.records }
    require(rawRecords.isNotEmpty()) { "UIGF file contains no gacha records" }
    val sanitizedData = rawRecords.map { record ->
        record.copy(itemType = record.sanitizeItemName())
    }
    Pair(wrapper.info.uid, sanitizedData)
}

fun GachaRecord.sanitizeItemName(): String {
    return when (this.itemType) {
        "角色", "character", "Character" -> "character"
        "武器", "weapon", "Weapon" -> "weapon"
        else -> this.itemType.lowercase()
    }
}

fun compareGachaRecordIds(a: String, b: String): Int {
    if (a.isBlank() && b.isBlank()) return 0
    if (a.isBlank()) return -1
    if (b.isBlank()) return 1
    val aNum = a.toULongOrNull()
    val bNum = b.toULongOrNull()
    if (aNum != null && bNum != null) return aNum.compareTo(bNum)
    return a.compareTo(b)
}

fun GachaRecord.compareChronologically(other: GachaRecord): Int {
    if (recordID.isNotBlank() && other.recordID.isNotBlank()) {
        val byId = compareGachaRecordIds(recordID, other.recordID)
        if (byId != 0) return byId
    }
    return time.compareTo(other.time)
}

val gachaRecordChronologicalComparator: Comparator<GachaRecord> =
    Comparator { a, b -> a.compareChronologically(b) }

fun List<GachaRecord>.sortedChronologically(): List<GachaRecord> =
    sortedWith(gachaRecordChronologicalComparator)

fun List<GachaRecord>.sortedChronologicallyDescending(): List<GachaRecord> =
    sortedWith(gachaRecordChronologicalComparator.reversed())