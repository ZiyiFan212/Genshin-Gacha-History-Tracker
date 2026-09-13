package utilities

import assets.I18nManager
import assets.IconManager
import assets.ItemTranslator
import assets.UpTimeLoader
import storage.IOConfiguration

object AppBootstrap {

    @Volatile
    private var initialized: Boolean = false

    fun isInitialized(): Boolean = initialized

    fun requireInitialized() {
        check(initialized) {
            "Application is not initialized. Call AppBootstrap.initialize() before using export or asset features."
        }
    }

    suspend fun initialize(locale: String? = null): Result<Unit> = runCatching {
        if (initialized) return@runCatching

        IOConfiguration.initialize().getOrThrow()
        PreferencesManager.loadUserPreferences().getOrThrow()

        val resolvedLocale = locale ?: PreferencesManager.getLanguageOrDefault()
        I18nManager.load(resolvedLocale).getOrThrow()
        ItemTranslator.load().getOrThrow()
        IconManager.init().getOrThrow()
        UpTimeLoader.load().getOrThrow()

        initialized = true
    }
}