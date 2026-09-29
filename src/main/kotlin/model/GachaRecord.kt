package model

import kotlinx.serialization.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

@Serializable
data class GachaRecord(
    @SerialName("gacha_type") val gachaType: String,
    val time: String,
    val name: String,
    @SerialName("item_type") val itemType: String,
    @SerialName("item_id") val itemID: String,
    @SerialName("id") val recordID: String,
    @SerialName("rank_type") val rankType: Int,
    @SerialName("uigf_gacha_type") val uigfGachaType: String = if (gachaType == "400") "301" else gachaType,
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

@Serializable
private data class UigfV41pair(val uid: String, val list: List<GachaRecord>)

@Serializable
private data class UigfV41Archive(val hk4e: List<UigfV41pair>)

fun parseJson(jsonString: String): Result<Pair<String, List<GachaRecord>>> = runCatching {
    val root = customizeJson.parseToJsonElement(jsonString).jsonObject
    val (uid, rawRecords) = if ("hk4e" in root) {
        val archive = customizeJson.decodeFromJsonElement<UigfV41Archive>(root)
        require(archive.hk4e.size == 1) {
            "Import requires exactly one Genshin Impact account; export each UID separately."
        }
        archive.hk4e.single().let { it.uid to it.list }
    } else {
        val wrapper = customizeJson.decodeFromJsonElement<UIGFWrapper>(root)
        wrapper.info.uid to wrapper.list.ifEmpty { wrapper.records }
    }


    require(rawRecords.isNotEmpty()) { "UIGF file does not contain any records!" }
    val sanitizedData = rawRecords.map { record ->
        record.copy(itemType = record.sanitizeItemName())
    }
    Pair(uid, sanitizedData)
}

// sanitize the name of the item type (from zh-cn to en-us)
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
