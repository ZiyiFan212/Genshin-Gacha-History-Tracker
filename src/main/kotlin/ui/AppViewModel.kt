package ui

import model.BannerStats
import model.LuckAnalysis
import model.MonthlyConsumption
import model.PityState
import model.TimelineEntry
import model.GoldPullSegment
import model.StreakAnalysis
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
import model.CapturePhase
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
import logger.LogBody
import model.Severity
import logger.LogWriter
import excelWriter.ExcelWriter
import storage.Database
import storage.ImportInvalidatedException
import model.GachaRecord
import model.UserStatistics
import model.parseJson
import utilities.AppBootstrap
import utilities.preference.PreferencesManager
import utilities.preference.ThemeModeManager
import analytics.upRatioCalculator
import assets.ItemTranslator
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utilities.safeUserMessage
import validation.DataValidator
import model.ValidationReport
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
    val calendarDays: Map<String, model.CalendarDay> = emptyMap(),
    val lastImport: ImportResult? = null,
    val isCapturing: Boolean = false,
    val capturePhase: CapturePhase? = null,
)

class AppViewModel {
    private val scope = CoroutineScope(Dispatchers.Main)
    private val UiState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = UiState.asStateFlow()
    private val proxyService = ProxyService()
    private val preferencesMutex = Mutex()

    // run by lazy, particularly for the cache
    private var analyticsCache: AnalyticsCache? = null
    private var analyticsCacheUid: String? = null


    private data class AnalyticsCache(
        val bannerStats: List<BannerStats>,
        val timeline: List<TimelineEntry>,
        val goldHistory: Map<String, List<GoldPullSegment>>,
        val calendarDays: Map<String, model.CalendarDay>,
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
            LogWriter.instance.tryLog(LogBody(Severity.INFO, "Analytics cache computed for uid=$uid"))
            return cache
        }
    }

    val bannerStats: List<BannerStats> get() = getAnalyticsAsCache().bannerStats
    val timeline: List<TimelineEntry> get() = getAnalyticsAsCache().timeline
    val goldHistory: Map<String, List<GoldPullSegment>> get() = getAnalyticsAsCache().goldHistory
    val calendarDays: Map<String, model.CalendarDay> get() = getAnalyticsAsCache().calendarDays

    private fun invalidateAnalyticsCache() {
        analyticsCache = null
        analyticsCacheUid = null
    }

    fun initialize() {
        scope.launch {
            UiState.update { it.copy(isLoading = true, error = null) }
            val initialization = preferencesMutex.withLock {
                val result = AppBootstrap.initialize()
                currentCoroutineContext().ensureActive()
                result.onSuccess {
                    val prefs = PreferencesManager.loadUserPreferences().getOrDefault(PreferencesManager.UserPreferences())
                    currentCoroutineContext().ensureActive()
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
                }
                result
            }
            initialization.onSuccess {
                refreshUidList()
                LogWriter.instance.tryLog(LogBody(Severity.INFO, "Application initialized"))
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Initialization failed", e))
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
            startUidLoad(uid)
        }
    }
    // Accessed only on Main, including refreshes after database maintenance.
    private var selectionJob: Job? = null
    private var selectionRequest = 0L

    private fun startUidLoad(uid: String) {
        val request = ++selectionRequest
        selectionJob?.cancel()
        invalidateAnalyticsCache()
        UiState.update {
            it.nullAllData().copy(selectedUid = uid, isLoading = true, error = null)
        }
        selectionJob = scope.launch {
            val result = loadUid(uid)
            currentCoroutineContext().ensureActive()
            if (request != selectionRequest) return@launch
            invalidateAnalyticsCache()
            result.onSuccess { data ->
                UiState.update {
                    it.copy(
                        isLoading = false, records = data.records, stats = data.stats,
                        pityState = data.pityState, luck = data.luck,
                        consumption = data.consumption, streakAnalysis = data.streakAnalysis,
                    )
                }
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to load uid=$uid", e))
                UiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun AppUiState.nullAllData() = copy(
        records = emptyList(), stats = null, pityState = null,
        bannerStats = emptyList(), timeline = emptyList(), luck = null, consumption = emptyList(),
        streakAnalysis = null, goldHistory = emptyMap(), calendarDays = emptyMap(),
    )

    fun getCurrentUID(): String {
        return UiState.value.selectedUid ?: ""
    }

    fun importFromPath(path: Path) {
        scope.launch {
            UiState.update { it.copy(isLoading = true, error = null) }
            withContext(Dispatchers.IO) {
                runCatching {
                    val importTicket = Database.beginImport().getOrThrow()
                    BackupManager.backupBeforeImport().getOrThrow()
                    val json = path.readText()
                    val (uid, imported) = parseJson(json).getOrThrow()
                    val validation = DataValidator.validate(imported, uid, ItemTranslator::containsId)
                    if (validation.hasErrors) {
                        throw IllegalStateException(
                            validation.issues.first { it.severity == Severity.ERROR }.message
                        )
                    }

                    val result = Database.mergeRecords(uid, imported, importTicket).getOrThrow()
                    val merged = result.records
                    val newCount = result.newCount

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
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Import failed", e))
                    UiState.update {
                        it.copy(isLoading = false, error = if (e is ImportInvalidatedException) e.safeUserMessage() else e.message ?: "Import failed")
                    }
                }
            }
        }
    }


    private val isProxyLaunched = AtomicBoolean(false)
    /**
     * The function launches the proxy service to update the records.
     * Validation, merging, updating database happen subsequently.
     *
     * Only ONE proxy entity should exist at the same time. Hence, the function is locked with atomic boolean,
     * so another thread trying to launch while the service is running is prohibited!!
     */
    fun captureFromProxy() {
        if (!isProxyLaunched.compareAndSet(false, true)) return

        scope.launch {
            UiState.update {
                it.copy(isCapturing = true, capturePhase = CapturePhase.STARTING, error = null)
            }
            try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val importTicket = Database.beginImport().getOrThrow()
                        BackupManager.backupBeforeImport().getOrThrow()

                        val (uid, imported) = proxyService.captureGachaRecords(
                            onPhase = { phase -> UiState.update { it.copy(capturePhase = phase) } },
                        ).getOrThrow()
                        val validation = DataValidator.validate(imported, uid, ItemTranslator::containsId)
                        if (validation.hasErrors) {
                            throw IllegalStateException(
                                validation.issues.first { it.severity == Severity.ERROR }.message
                            )
                        }

                        val result = Database.mergeRecords(uid, imported, importTicket).getOrThrow()
                        val merged = result.records
                        val newCount = result.newCount

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
                        LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Proxy capture failed", e))
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
        val snapshot = UiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            UigfExporter.export(uid to records, version)
                .onSuccess { path ->
                    UiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") }
                    LogWriter.instance.tryLog(LogBody(Severity.INFO, "UIGF export: ignoreThreeStar is always false for UIGF format, exported to $path"))
                }
                .onFailure { e ->
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "UIGF export failed", e))
                    UiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun exportExcel() {
        val snapshot = UiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            runCatching {
                ExcelWriter.exportExcel(mapOf(uid to records))
            }.onSuccess {
                UiState.update { it.copy(message = I18nManager["message.export_success"]) }
                LogWriter.instance.tryLog(LogBody(Severity.INFO, "Excel exported for uid=$uid"))
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Excel export failed", e))
                UiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun exportCsv() {
        val snapshot = UiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            CsvExporter.export(records, uid, ignoreThreeStar = snapshot.ignoreThreeStarExport)
                .onSuccess { path -> UiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") } }
                .onFailure { e -> UiState.update { it.copy(error = e.message) } }
        }
    }

    fun exportHtml() {
        val snapshot = UiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            HtmlExporter.export(records, uid, ignoreThreeStar = snapshot.ignoreThreeStarExport)
                .onSuccess { path -> UiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") } }
                .onFailure { e -> UiState.update { it.copy(error = e.message) } }
        }
    }

    fun setThemeMode(mode: ThemeModeManager) {
        updatePreferences { it.copy(themeMode = mode) }
    }

    fun setLanguage(lang: String) {
        updatePreferences { it.copy(language = lang) }
    }

    fun setShowAllItems(show: Boolean) {
        updatePreferences { it.copy(showAllItems = show) }
    }

    fun setIgnoreThreeStarExport(ignore: Boolean) {
        updatePreferences { it.copy(ignoreThreeStarExport = ignore) }
    }

    /**
     * A function to update any user's preference.
     * Callback accepts the current UI state, returning it after update.
     *
     * We use lock to ensure that reading after acquiring the lock so queued changes retain earliersettings.
     */
    private fun updatePreferences(transform: (AppUiState) -> AppUiState) {
        scope.launch {
            preferencesMutex.withLock {
                val current = UiState.value
                val updated = transform(current)
                PreferencesManager.saveUserPreferences(
                    updated.themeMode, updated.language, updated.showAllItems, updated.ignoreThreeStarExport,
                )
                // Preference persistence wraps failures, including cancellation, in Result.
                currentCoroutineContext().ensureActive()
                if (updated.language != current.language) I18nManager.load(updated.language)
                UiState.update {
                    // Preserve account data, navigation, and messages changed during the IO suspension.
                    it.copy(
                        themeMode = updated.themeMode,
                        language = updated.language,
                        showAllItems = updated.showAllItems,
                        ignoreThreeStarExport = updated.ignoreThreeStarExport,
                    )
                }
            }
        }
    }

    // database disconnected & all coroutines canceled
    fun shutdown() {
        LogWriter.instance.tryLog(LogBody(Severity.INFO, "Application shutting down: disconnecting the database and cancelling all running coroutines."))
        scope.cancel()
        Database.dbClose()
    }

    fun deleteCurrentUid() {
        val uid = UiState.value.selectedUid ?: return
        scope.launch {
            Database.delete(uid).onSuccess {
                refreshUidList()
                val next = UiState.value.uids.firstOrNull()
                if (next != null) selectUid(next)
                else {
                    ++selectionRequest
                    selectionJob?.cancel()
                    UiState.update {
                        it.nullAllData().copy(selectedUid = null, isLoading = false)
                    }
                }
                invalidateAnalyticsCache()
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to delete uid=$uid", e))
                UiState.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun refreshUidList() {
        val uids = Database.listAllUids().getOrDefault(emptyList())
        UiState.update { it.copy(uids = uids) }
        if (UiState.value.selectedUid == null && uids.isNotEmpty()) {
            startUidLoad(uids.first())
        }
    }

    private data class LoadedAccount(
        val records: List<GachaRecord>,
        val stats: UserStatistics?,
        val pityState: PityState,
        val luck: LuckAnalysis,
        val consumption: List<MonthlyConsumption>,
        val streakAnalysis: StreakAnalysis,
    )

    private suspend fun loadUid(uid: String): Result<LoadedAccount> =
        withContext(Dispatchers.IO) {
            val result = Database.search(uid)
            currentCoroutineContext().ensureActive()
            result.mapCatching { data ->
                val records = data?.first ?: emptyList()
                LoadedAccount(
                    records = records, stats = data?.second,
                    pityState = records.pityState(), luck = records.analyzeLuck(),
                    consumption = records.monthlyConsumption(), streakAnalysis = records.analyzeStreaks(),
                )
            }
        }

    fun updateStats() {
        val uid = UiState.value.selectedUid ?: return
        scope.launch {
            val request = selectionRequest
            withContext(Dispatchers.IO) {
                Database.updateStats(uid).onSuccess {
                    withContext(Dispatchers.Main) {
                        if (selectionRequest == request && UiState.value.selectedUid == uid) startUidLoad(uid)
                    }
                }.onFailure { e ->
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to update stats for uid=$uid", e))
                }
            }
        }
    }

    fun updateRecords() {
        val uid = UiState.value.selectedUid ?: return
        //AppLogger.debug("begin reassign id")
        scope.launch {
            val request = selectionRequest
            withContext(Dispatchers.IO) {
                Database.updateRecords(uid) { records ->
                    records.map { it.copy(itemID = ItemTranslator.getIdByName(it.name).ifBlank { it.itemID }) }
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        if (selectionRequest == request && UiState.value.selectedUid == uid) startUidLoad(uid)
                    }
                }.onFailure { e ->
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to update records for uid=$uid", e))
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

