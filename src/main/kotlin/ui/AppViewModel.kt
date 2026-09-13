package ui

import analytics.BannerStats
import analytics.LuckAnalysis
import analytics.MonthlyConsumption
import analytics.PityState
import analytics.TimelineEntry
import analytics.GoldPullSegment
import analytics.StreakAnalysis
import analytics.analyzeStreaks
import analytics.buildGoldHistory
import analytics.analyzeLuck
import analytics.buildTimeline
import analytics.calendarDays
import analytics.limitedBannerStats
import analytics.monthlyConsumption
import analytics.pityState
import analytics.longestNoPullIntervalDays
import analytics.limitedWeaponAvgPity

import assets.I18nManager
import backup.BackupManager
import core.CapturePhase
import core.ProxyService
import storage.CsvExporter
import storage.HtmlExporter
import storage.UigfExporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import utilities.AppLogger
import excelWriter.ExcelWriter
import storage.AppDatabase
import model.GachaRecord
import model.UserStatistics
import model.parseJson
import utilities.AppBootstrap
import utilities.PreferencesManager
import utilities.ThemeModeManager
import analytics.calculateStat
import analytics.upRatioCalculator
import assets.ItemTranslator
import kotlinx.coroutines.cancel
import utilities.mergeWith
import utilities.safeUserMessage
import validation.DataValidator
import validation.Severity
import validation.ValidationReport
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.readText

enum class AppScreen { HOME, CAPTURE, IMPORT, STATS, TIMELINE, CALENDAR, EXPORT, SETTINGS }

data class ImportResult(
    val uid: String,
    val newRecords: Int,
    val totalRecords: Int,
    val validation: ValidationReport,
)

data class AppUiState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val currentScreen: AppScreen = AppScreen.HOME,
    val themeMode: ThemeModeManager = ThemeModeManager.SYSTEM,
    val language: String = "en",
    val showAllItems: Boolean = false,
    val ignoreThreeStarExport: Boolean = false,
    val uids: List<String> = emptyList(),
    val selectedUid: String? = null,
    val records: List<GachaRecord> = emptyList(),
    val stats: UserStatistics? = null,
    val pityState: PityState? = null,
    val bannerStats: List<BannerStats> = emptyList(),
    val timeline: List<TimelineEntry> = emptyList(),
    val luck: LuckAnalysis? = null,
    val consumption: List<MonthlyConsumption> = emptyList(),
    val streakAnalysis: StreakAnalysis? = null,
    val goldHistory: Map<String, List<GoldPullSegment>> = emptyMap(),
    val calendarDays: Map<String, analytics.CalendarDay> = emptyMap(),
    val lastImport: ImportResult? = null,
    val isCapturing: Boolean = false,
    val capturePhase: CapturePhase? = null,
)

class AppViewModel {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val UiState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = UiState.asStateFlow()
    private val proxyService = ProxyService()

    // run by lazy, particularly for the cache
    private var analyticsCache: AnalyticsCache? = null
    private var analyticsCacheUid: String? = null


    private data class AnalyticsCache(
        val bannerStats: List<BannerStats>,
        val timeline: List<TimelineEntry>,
        val goldHistory: Map<String, List<GoldPullSegment>>,
        val calendarDays: Map<String, analytics.CalendarDay>,
    )

    private fun getAnalyticsAsCache(): AnalyticsCache {
        synchronized(this) {
            val uid = UiState.value.selectedUid
            val records = UiState.value.records
            if (analyticsCache != null && analyticsCacheUid == uid) {
                return analyticsCache!!
            }
            val cache = AnalyticsCache(
                bannerStats = records.limitedBannerStats(),
                timeline = records.buildTimeline(),
                goldHistory = records.buildGoldHistory(),
                calendarDays = records.calendarDays(),
            )
            analyticsCache = cache
            analyticsCacheUid = uid
            AppLogger.info("Analytics cache computed for uid=$uid")
            return cache
        }
    }

    val bannerStats: List<BannerStats> get() = getAnalyticsAsCache().bannerStats
    val timeline: List<TimelineEntry> get() = getAnalyticsAsCache().timeline
    val goldHistory: Map<String, List<GoldPullSegment>> get() = getAnalyticsAsCache().goldHistory
    val calendarDays: Map<String, analytics.CalendarDay> get() = getAnalyticsAsCache().calendarDays

    private fun invalidateAnalyticsCache() {
        analyticsCache = null
        analyticsCacheUid = null
    }

    fun initialize() {
        scope.launch {
            UiState.update { it.copy(isLoading = true, error = null) }
            AppBootstrap.initialize().onSuccess {
                val prefs = PreferencesManager.loadUserPreferences().getOrDefault(PreferencesManager.UserPreferences())
                I18nManager.load(prefs.language)
                UiState.update {
                    it.copy(
                        isLoading = false,
                        themeMode = prefs.themeMode,
                        language = prefs.language,
                        showAllItems = prefs.showAllItems,
                        ignoreThreeStarExport = prefs.ignoreThreeStarExport,
                    )
                }
                refreshUidList()
                AppLogger.info("Application initialized")
            }.onFailure { e ->
                AppLogger.error("Initialization failed", e)
                UiState.update { it.copy(isLoading = false, error = e.message ?: "Init failed") }
            }
        }
    }

    fun navigate(screen: AppScreen) {
        UiState.update { it.copy(currentScreen = screen, message = null, error = null) }
    }

    fun clearMessage() {
        UiState.update { it.copy(message = null, error = null) }
    }

    fun selectUid(uid: String) {
        scope.launch {
            UiState.update { it.copy(isLoading = true, selectedUid = uid) }
            loadUid(uid)
        }
    }

    fun getCurrentUID(): String {
        return UiState.value.selectedUid ?: ""
    }

    fun importFromPath(path: Path) {
        scope.launch {
            UiState.update { it.copy(isLoading = true, error = null) }
            withContext(Dispatchers.IO) {
                runCatching {
                    BackupManager.backupBeforeImport().getOrThrow()
                    val json = path.readText()
                    val (uid, imported) = parseJson(json).getOrThrow()
                    val validation = DataValidator.validate(imported, uid)
                    if (validation.hasErrors) {
                        throw IllegalStateException(
                            validation.issues.first { it.severity == Severity.ERROR }.message
                        )
                    }

                    val existing = AppDatabase.search(uid).getOrThrow()?.first ?: emptyList()
                        val merged = imported.mergeWith(existing)
                        val newCount = maxOf(0, merged.size - existing.size)
                        val stats = merged.calculateStat()
                    AppDatabase.upsert(uid to merged, stats).getOrThrow()

                    withContext(Dispatchers.Main) {
                        UiState.update {
                            it.copy(
                                isLoading = false,
                                message = I18nManager["message.import_success", "count" to newCount.toString()],
                                lastImport = ImportResult(uid, newCount, merged.size, validation),
                            )
                        }
                        refreshUidList()
                        selectUid(uid)
                    }
                }.onFailure { e ->
                    AppLogger.error("Import failed", e)
                    UiState.update { it.copy(isLoading = false, error = e.message ?: "Import failed") }
                }
            }
        }
    }

    // lock with atomic boolean, only one thread can start capturing service
    private val isProxyLaunched = AtomicBoolean(false)
    fun captureFromProxy() {
        if (!isProxyLaunched.compareAndSet(false, true)) return

        scope.launch {
            UiState.update {
                it.copy(isCapturing = true, capturePhase = CapturePhase.STARTING, error = null)
            }
            try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        BackupManager.backupBeforeImport().getOrThrow()

                        val (uid, imported) = proxyService.captureGachaRecords(
                            onPhase = { phase -> UiState.update { it.copy(capturePhase = phase) } },
                            currentUid = UiState.value.selectedUid
                        ).getOrThrow()
                        val validation = DataValidator.validate(imported, uid)
                        if (validation.hasErrors) {
                            throw IllegalStateException(
                                validation.issues.first { it.severity == Severity.ERROR }.message
                            )
                        }

                        val existing = AppDatabase.search(uid).getOrThrow()?.first ?: emptyList()
                        val merged = imported.mergeWith(existing)
                        val newCount = (merged.size - existing.size).coerceAtLeast(0)
                        val stats = merged.calculateStat()
                        AppDatabase.upsert(uid to merged, stats).getOrThrow()

                        withContext(Dispatchers.Main) {
                            UiState.update {
                                it.copy(
                                    message = I18nManager["message.import_success", "count" to newCount.toString()],
                                    lastImport = ImportResult(uid, newCount, merged.size, validation),
                                )
                            }
                            refreshUidList()
                            selectUid(uid)
                            navigate(AppScreen.TIMELINE)
                        }
                    }.onFailure { e ->
                        AppLogger.error("Proxy capture failed", e)
                        UiState.update { it.copy(error = e.safeUserMessage()) }
                    }
                }
            } finally {
                UiState.update { it.copy(isCapturing = false, capturePhase = null) }
                isProxyLaunched.set(false)
            }
        }
    }

    fun exportUIGF(version: String) {
        val uid = UiState.value.selectedUid ?: return
        val records = UiState.value.records
        scope.launch(Dispatchers.IO) {
            UigfExporter.export(uid to records, version)
                .onSuccess { path ->
                    UiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") }
                    AppLogger.info("UIGF export: ignoreThreeStar is always false for UIGF format, exported to $path")
                }
                .onFailure { e ->
                    AppLogger.error("UIGF export failed", e)
                    UiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun exportExcel() {
        val uid = UiState.value.selectedUid ?: return
        val records = UiState.value.records
        scope.launch(Dispatchers.IO) {
            runCatching {
                ExcelWriter.exportExcel(mapOf(uid to records))
            }.onSuccess {
                UiState.update { it.copy(message = I18nManager["message.export_success"]) }
                AppLogger.info("Excel exported for uid=$uid")
            }.onFailure { e ->
                AppLogger.error("Excel export failed", e)
                UiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun exportCsv() {
        val uid = UiState.value.selectedUid ?: return
        scope.launch(Dispatchers.IO) {
            CsvExporter.export(UiState.value.records, uid, ignoreThreeStar = UiState.value.ignoreThreeStarExport)
                .onSuccess { path -> UiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") } }
                .onFailure { e -> UiState.update { it.copy(error = e.message) } }
        }
    }

    fun exportHtml() {
        val uid = UiState.value.selectedUid ?: return
        scope.launch(Dispatchers.IO) {
            HtmlExporter.export(UiState.value.records, uid, ignoreThreeStar = UiState.value.ignoreThreeStarExport)
                .onSuccess { path -> UiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") } }
                .onFailure { e -> UiState.update { it.copy(error = e.message) } }
        }
    }

    fun setThemeMode(mode: ThemeModeManager) {
        scope.launch {
            PreferencesManager.saveUserPreferences(mode, UiState.value.language, UiState.value.showAllItems, UiState.value.ignoreThreeStarExport)
            UiState.update { it.copy(themeMode = mode) }
        }
    }

    fun setLanguage(lang: String) {
        scope.launch {
            PreferencesManager.saveUserPreferences(UiState.value.themeMode, lang, UiState.value.showAllItems, UiState.value.ignoreThreeStarExport)
            I18nManager.load(lang)
            UiState.update { it.copy(language = lang) }
        }
    }

    fun setShowAllItems(show: Boolean) {
        scope.launch {
            PreferencesManager.saveUserPreferences(UiState.value.themeMode, UiState.value.language, show, UiState.value.ignoreThreeStarExport)
            UiState.update { it.copy(showAllItems = show) }
        }
    }

    fun setIgnoreThreeStarExport(ignore: Boolean) {
        scope.launch {
            PreferencesManager.saveUserPreferences(UiState.value.themeMode, UiState.value.language, UiState.value.showAllItems, ignore)
            UiState.update { it.copy(ignoreThreeStarExport = ignore) }
        }
    }

    // database disconnected & all coroutines canceled
    fun shutdown() {
        AppLogger.info("Application shutting down: disconnecting the database and cancelling all running coroutines.")
        AppDatabase.dbClose()
        scope.cancel()
    }

    fun deleteCurrentUid() {
        val uid = UiState.value.selectedUid ?: return
        scope.launch {
            AppDatabase.delete(uid).onSuccess {
                refreshUidList()
                val next = UiState.value.uids.firstOrNull()
                if (next != null) selectUid(next)
                else UiState.update {
                    it.copy(selectedUid = null, records = emptyList(), stats = null, pityState = null,
                        bannerStats = emptyList(), timeline = emptyList(), luck = null, consumption = emptyList(),
                        streakAnalysis = null, goldHistory = emptyMap(), calendarDays = emptyMap())
                }
                invalidateAnalyticsCache()
            }
        }
    }

    private suspend fun refreshUidList() {
        val uids = AppDatabase.listAllUids().getOrDefault(emptyList())
        UiState.update { it.copy(uids = uids) }
        if (UiState.value.selectedUid == null && uids.isNotEmpty()) {
            loadUid(uids.first())
        }
    }

    private suspend fun loadUid(uid: String) {
        withContext(Dispatchers.IO) {
            AppDatabase.search(uid).onSuccess { data ->
                val records = data?.first ?: emptyList()
                val stats = data?.second
                UiState.update {
                    it.copy(
                        isLoading = false,
                        selectedUid = uid,
                        records = records,
                        stats = stats,
                        pityState = records.pityState(),
                        luck = records.analyzeLuck(),
                        consumption = records.monthlyConsumption(),
                        streakAnalysis = records.analyzeStreaks(),
                    )
                }
                invalidateAnalyticsCache()
            }.onFailure { e ->
                AppLogger.error("Failed to load uid=$uid", e)
                UiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateStats() {
        val uid = UiState.value.selectedUid ?: return
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getRecord(uid).onSuccess { records ->

                    // update the statistics
                    val newStats = records.calculateStat()
                    AppDatabase.updateCurrentUser(uid, newStats)
                    UiState.update { it.copy(stats = newStats) }
                }.onFailure { e ->
                    AppLogger.error("Failed to update stats for uid=$uid", e)
                }
            }
        }
    }

    fun updateRecords() {
        val uid = UiState.value.selectedUid ?: return
        //AppLogger.debug("begin reassign id")
        scope.launch {
            withContext(Dispatchers.IO) {
                AppDatabase.getRecord(uid).onSuccess { records ->
                    val updated = records.map {

                            it.copy(itemID = ItemTranslator.getIdByName(it.name))

                    }
                    AppDatabase.updateRecords(uid, updated)
                    UiState.update { it.copy(records = updated) }
                    invalidateAnalyticsCache()
                }.onFailure { e ->
                    AppLogger.error("Failed to update records for uid=$uid", e)
                }
            }
        }
        //AppLogger.debug("over")
    }

    val longestNoPullDays: Int
        get() = state.value.records.longestNoPullIntervalDays()

    val limitedWeaponAvgPity: Double
        get() = state.value.records.limitedWeaponAvgPity()

    val upRatio: Pair<Double, Double>
        get() = state.value.records.upRatioCalculator()
}

private fun <T> Result<T>.getOrDefault(default: T): T = getOrElse { default }

