package template

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

// get uid from Info
@Serializable
data class Info(
    val uid: String,
)

// wrapped Info and Record
@Serializable
data class UIGFWrapper(
    val info: Info,
    @SerialName("list") val records: List<GachaRecord>
)
val customizeJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

// deserialization: flatten the json file
fun parseJson(jsonString: String): Pair<String, List<GachaRecord>>{
    val wrapper = customizeJson.decodeFromString<UIGFWrapper>(jsonString)
    return Pair(wrapper.info.uid, wrapper.records)
}


