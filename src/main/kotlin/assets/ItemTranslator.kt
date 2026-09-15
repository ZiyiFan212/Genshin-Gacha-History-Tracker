package assets

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import utilities.PreferencesManager
import java.util.MissingResourceException

object ItemTranslator {

    private const val TRANSLATOR_PATH = "/gacha-assets/items/translator.json"

    @Serializable
    data class Name(val en: String, val zh: String)

    private val json = Json { ignoreUnknownKeys = true }
    private var itemTranslationMap = emptyMap<String, Name>()
    private var nameToIdMap = emptyMap<String, String>()
    private var initialized = false

    fun isLoaded(): Boolean = initialized

    fun load(): Result<Unit> = runCatching {
        val text = ItemTranslator::class.java.classLoader
            .getResourceAsStream(TRANSLATOR_PATH.trimStart('/'))
            ?.bufferedReader()?.readText()
            ?: throw MissingResourceException(
                "Missing item translation", "ItemTranslator", "translator.json"
            )
        itemTranslationMap = json.decodeFromString<Map<String, Name>>(text)
        nameToIdMap = buildNameToIdMap(itemTranslationMap)
        initialized = true
    }

    /**
     * Based on the loaded item translation map [itemTranslationMap], we map the name to item ID.
     * This ensures each item has its corresponding ID after fetching from the server.
     */
    private fun buildNameToIdMap(translationMap: Map<String, Name>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        translationMap.forEach { (id, name) ->
            map[name.zh] = id
            map[name.en] = id
        }
        return map
    }

    operator fun get(id: String): String {
        check(initialized) { "ItemTranslator has not been loaded" }
        val name = itemTranslationMap[id] ?: return "Unknown item: $id"
        return when (PreferencesManager.getLanguageOrDefault()) {
            "zh" -> name.zh
            else -> name.en
        }
    }

    fun getIdByName(name: String): String {
        check(initialized) { "ItemTranslator has not been loaded" }
        return nameToIdMap[name] ?: ""
    }

    /** Resolve export names without changing the user's interface language. */
    fun getExportName(id: String, language: String): String? {
        if (!initialized) load().getOrThrow()
        val name = itemTranslationMap[id] ?: return null
        return if (language == "zh-cn") name.zh else name.en
    }

    // just for testing
    /*
    fun returnMap(): Map<String, String> {
        return nameToIdMap
    }
     */

}
