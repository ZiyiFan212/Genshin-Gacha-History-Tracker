package utilities

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import storage.IOConfiguration
import java.time.LocalDateTime
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText

object PreferencesManager {

    @Serializable
    private data class UserPreferencesDto(
        val isWhiteTheme: Boolean? = null,
        val themeMode: ThemeModeManager? = null,
        val language: String = "en",
        val showAllItems: Boolean = false,
        val ignoreThreeStarExport: Boolean = false,
    )

    @Serializable
    data class UserPreferences(
        val themeMode: ThemeModeManager = ThemeModeManager.SYSTEM,
        val language: String = "en",
        val showAllItems: Boolean = false,
        val ignoreThreeStarExport: Boolean = false,
    )

    private val file = IOConfiguration.default_configPath.resolve("preferences.json")
    private val jsonProcessor = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private var cachedPreferences = UserPreferences()
    private var initialized = false

    suspend fun loadUserPreferences(): Result<UserPreferences> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (file.exists()) {
                    val dto = jsonProcessor.decodeFromString<UserPreferencesDto>(file.readText())
                    cachedPreferences = dto.toResolved()
                } else {
                    file.parent.let { parent ->
                        if (!parent.toFile().exists()) {
                            parent.toFile().mkdirs()
                        }
                    }
                    file.writeText(jsonProcessor.encodeToString(UserPreferencesDto(themeMode = ThemeModeManager.SYSTEM)))
                }
                initialized = true
                cachedPreferences
            }
        }

    suspend fun saveUserPreferences(themeMode: ThemeModeManager, language: String, showAllItems: Boolean = false, ignoreThreeStarExport: Boolean = false): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val newPrefs = UserPreferences(themeMode, language, showAllItems, ignoreThreeStarExport)
                file.parent.let { parent ->
                    if (!parent.toFile().exists()) {
                        parent.toFile().mkdirs()
                    }
                }
                file.writeText(
                    jsonProcessor.encodeToString(
                        UserPreferencesDto(
                            themeMode = themeMode,
                            language = language,
                            showAllItems = showAllItems,
                            ignoreThreeStarExport = ignoreThreeStarExport,
                        )
                    )
                )
                cachedPreferences = newPrefs
                initialized = true
            }
        }

    /** @deprecated Use [saveUserPreferences] with [ThemeModeManager] instead. */
    suspend fun saveUserPreferences(isWhiteTheme: Boolean, language: String): Result<Unit> =
        saveUserPreferences(
            themeMode = if (isWhiteTheme) ThemeModeManager.LIGHT else ThemeModeManager.DARK,
            language = language,
        )

    fun isInitialized(): Boolean = initialized

    fun getLanguageOrDefault(): String =
        if (initialized) cachedPreferences.language else "en"

    fun getLanguage(): String {
        check(initialized) { "PreferencesManager has not been loaded" }
        return cachedPreferences.language
    }

    fun getThemeMode(): ThemeModeManager {
        check(initialized) { "PreferencesManager has not been loaded" }
        return cachedPreferences.themeMode
    }

    fun getShowAllItems(): Boolean {
        check(initialized) { "PreferencesManager has not been loaded" }
        return cachedPreferences.showAllItems
    }

    fun getIgnoreThreeStarExport(): Boolean {
        check(initialized) { "PreferencesManager has not been loaded" }
        return cachedPreferences.ignoreThreeStarExport
    }

    private fun UserPreferencesDto.toResolved(): UserPreferences {
        val mode = themeMode ?: when (isWhiteTheme) {
            true -> ThemeModeManager.LIGHT
            false -> ThemeModeManager.DARK
            null -> ThemeModeManager.SYSTEM
        }
        return UserPreferences(mode, language, showAllItems, ignoreThreeStarExport)
    }

    // interface for overriding
    interface ThemeDetector {
        fun isSystemInDarkTheme(): Boolean
    }
    class CustomizedThemeDetector : ThemeDetector {
        // logic relies on system local time
        override fun isSystemInDarkTheme(): Boolean {
            val currentTime: LocalDateTime = LocalDateTime.now()
            val currentHour = currentTime.hour
            return currentHour !in 7..17
        }
    }
}