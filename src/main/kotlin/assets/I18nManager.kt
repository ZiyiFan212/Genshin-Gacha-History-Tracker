package assets

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.util.MissingResourceException

object I18nManager {
    private const val RESOURCE_ROOT = "/gacha-assets"

    private val json = Json { ignoreUnknownKeys = true }
    private val SHEET_NAME_SANITIZE = Regex("""[\[\]:*?/\\]""")
    private var translationMap: Map<String, String> = emptyMap()
    private var loaded = false

    var currentLocale: String = "en"
        private set

    fun isLoaded(): Boolean = loaded

    fun load(locale: String): Result<Unit> {
        return runCatching {
            val text =
                loadResourceText("$RESOURCE_ROOT/$locale.json")
                    ?: loadResourceText("$RESOURCE_ROOT/en.json")
                    ?: throw MissingResourceException(
                        "Missing i18n file for '$locale', fallback 'en' also not found", "I18n", locale
                    )
            currentLocale = locale
            translationMap = flattenJson(text)
            loaded = true
        }
    }

    @SafeVarargs
    operator fun get(key: String, vararg args: Pair<String, String>): String {
        var result = translationMap[key] ?: key
        args.forEach { (k, v) -> result = result.replace("{$k}", v) }
        return result
    }

    fun sheetName(key: String): String {
        val raw = get(key)
        val sanitized = raw.replace(SHEET_NAME_SANITIZE, "_").trim()
        return sanitized.take(31).ifEmpty { "Sheet" }
    }

    private fun flattenJson(text: String, prefix: String = ""): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val obj = json.parseToJsonElement(text).jsonObject
        fun traverse(node: JsonObject, path: String) {
            node.forEach { (k, v) ->
                val fullKey = if (path.isEmpty()) k else "$path.$k"
                when (v) {
                    is JsonObject -> traverse(v.jsonObject, fullKey)
                    is JsonArray -> map[fullKey] = v.joinToString(", ") { element ->
                        when (element) {
                            is JsonPrimitive -> element.content
                            else -> element.toString()
                        }
                    }
                    is JsonPrimitive -> map[fullKey] = v.content
                }
            }
        }
        traverse(obj, prefix)
        return map
    }

    private fun loadResourceText(path: String): String? {
        val normalized = path.trimStart('/')
        val classLoader = I18nManager::class.java.classLoader
        val stream = classLoader.getResourceAsStream(normalized) ?: return null
        return stream.bufferedReader().use { it.readText() }
    }
}
