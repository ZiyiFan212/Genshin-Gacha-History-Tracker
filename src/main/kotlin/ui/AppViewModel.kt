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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import utilities.safeUserMessage
import validation.DataValidator
import model.ValidationReport
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.io.path.readText
import kotlin.time.Duration.Companion.milliseconds

/**
 * State of the current UI. Matching all screens.
 * If new screen component is added, the state also needs to be renewed.
 */
enum class AppScreen { HOME, CAPTURE, IMPORT, STATS, TIMELINE, CALENDAR, EXPORT, SETTINGS }

// Wrapper for import result.
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
    val luck: LuckAnalysis? = null,
    val consumption: List<MonthlyConsumption> = emptyList(),
    val streakAnalysis: StreakAnalysis? = null,
    val analytics: AppViewModel.CachedAnalytics = AppViewModel.CachedAnalytics(),
    val analyticsDate: LocalDate? = null,
    val lastImport: ImportResult? = null,
    val isCapturing: Boolean = false,
    val capturePhase: CapturePhase? = null,
)

class AppViewModel internal constructor(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val searchAccount: suspend (String) -> Result<Pair<List<GachaRecord>, UserStatistics>?> = Database::search,
    private val today: () -> LocalDate = { LocalDate.now(utilities.SERVER_TIME_OFFSET) },
) {
    private val uiState = MutableStateFlow(AppUiState())
    val state: StateFlow<AppUiState> = uiState.asStateFlow()
    private val proxyService = ProxyService()
    private val preferencesMutex = Mutex()

    // Computed off Main and published with the matching records in one state update.
    data class CachedAnalytics(
        val bannerStats: List<BannerStats> = emptyList(),
        val timeline: List<TimelineEntry> = emptyList(),
        val goldHistory: Map<String, List<GoldPullSegment>> = emptyMap(),
        val calendarDays: Map<String, model.CalendarDay> = emptyMap(),
        val longestNoPullDays: Int = 0,
        val limitedWeaponAvgPity: Double = 0.0,
        val upRatio: Pair<Double, Double> = 0.0 to 0.0,
    )

    // load count data class, wrapper for all analysis. This is fetched from the database, not cached!!
    private data class LoadedAccount(
        val records: List<GachaRecord>,
        val stats: UserStatistics?,
        val pityState: PityState,
        val luck: LuckAnalysis,
        val consumption: List<MonthlyConsumption>,
        val streakAnalysis: StreakAnalysis,
        val analytics: CachedAnalytics,
        val day: LocalDate,
    )

    // UID selection SN and coroutine job that handles frequent selecting. Main only.
    private var selectionJob: Job? = null
    private var selectionRequest = 0L

    // Analytics-only refreshes must not discard completed database maintenance.
    private var accountRevision = 0L
    private var dateRefreshJob: Job? = null

    // proxy starting/avaliable reference.
    private val isProxyLaunched = AtomicBoolean(false)

    fun initialize() {
        scope.launch {
            uiState.update { it.copy(isLoading = true, error = null) }
            val initialization = preferencesMutex.withLock {
                val result = AppBootstrap.initialize()
                currentCoroutineContext().ensureActive()
                result.onSuccess {
                    val prefs = PreferencesManager.loadUserPreferences().getOrDefault(PreferencesManager.UserPreferences())
                    currentCoroutineContext().ensureActive()
                    I18nManager.load(prefs.language)
                    uiState.update {
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
                startDateRefresh()
                LogWriter.instance.tryLog(LogBody(Severity.INFO, "Application initialized"))
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Initialization failed", e))
                uiState.update { it.copy(isLoading = false, error = e.message ?: "Init failed") }
            }
        }
    }

    fun navigate(screen: AppScreen) {
        uiState.update { it.copy(currentScreen = screen, message = null, error = null) }
        if (screen in setOf(AppScreen.HOME, AppScreen.STATS, AppScreen.TIMELINE, AppScreen.CALENDAR)) {
            refreshAnalytics()
        }
    }

    fun clearMessage() {
        uiState.update { it.copy(message = null, error = null) }
    }

    fun selectUid(uid: String) {
        scope.launch {
            startUidLoad(uid)
        }
    }

    private fun startUidLoad(uid: String) {
        // cancel current coroutine if running.
        ++accountRevision
        val request = ++selectionRequest
        selectionJob?.cancel()
        uiState.update {
            it.nullAllData().copy(selectedUid = uid, isLoading = true, error = null)
        }

        selectionJob = scope.launch {
            val result = loadUid(uid)
            currentCoroutineContext().ensureActive()
            if (request != selectionRequest) return@launch// terminating if not matching.
            result.onSuccess { data ->
                uiState.update {
                    it.copy(
                        isLoading = false, records = data.records, stats = data.stats,
                        pityState = data.pityState, luck = data.luck,
                        consumption = data.consumption, streakAnalysis = data.streakAnalysis,
                        analytics = data.analytics, analyticsDate = data.day,
                    )
                }
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to load uid=$uid", e))
                uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    private fun AppUiState.nullAllData() = copy(
        records = emptyList(), stats = null, pityState = null,
        luck = null, consumption = emptyList(), streakAnalysis = null,
        analytics = CachedAnalytics(), analyticsDate = null,
    )

    // Main only. Both the request number and list identity guard background results.
    internal fun refreshAnalytics(dateChanged: Boolean = false) {
        // Get the snapshot, returning if the date is today.
        val snapshot = uiState.value
        val uid = snapshot.selectedUid ?: return
        if (snapshot.isLoading) return
        val day = today()
        if (dateChanged && snapshot.analyticsDate == day) return

        val request = ++selectionRequest
        selectionJob?.cancel()
        uiState.update { it.copy(isLoading = true, error = null) }
        selectionJob = scope.launch {
            val result = computeAccount(snapshot.records, snapshot.stats, day)
            currentCoroutineContext().ensureActive()
            val current = uiState.value
            if (request != selectionRequest || current.selectedUid != uid || current.records !== snapshot.records) {
                return@launch
            }
            result.onSuccess { data ->
                uiState.update {
                    it.copy(
                        isLoading = false, pityState = data.pityState, luck = data.luck,
                        consumption = data.consumption, streakAnalysis = data.streakAnalysis,
                        analytics = data.analytics, analyticsDate = data.day,
                    )
                }
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to analyze uid=$uid", e))
                uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    // Checking if the date has changed every 60 seconds.
    private fun startDateRefresh() {
        if (dateRefreshJob?.isActive == true) return
        dateRefreshJob = scope.launch {
            while (isActive) {
                delay(60_000.milliseconds)
                refreshAnalytics(dateChanged = true)
            }
        }
    }

    fun getCurrentUID(): String {
        return uiState.value.selectedUid ?: ""
    }

    fun importFromPath(path: Path) {
        scope.launch {
            uiState.update { it.copy(isLoading = true, error = null) }
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
                        uiState.update {
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
                    uiState.update {
                        it.copy(isLoading = false, error = if (e is ImportInvalidatedException) e.safeUserMessage() else e.message ?: "Import failed")
                    }
                }
            }
        }
    }

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
            uiState.update {
                it.copy(isCapturing = true, capturePhase = CapturePhase.STARTING, error = null)
            }
            try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val importTicket = Database.beginImport().getOrThrow()
                        BackupManager.backupBeforeImport().getOrThrow()

                        val (uid, imported) = proxyService.captureGachaRecords(
                            onPhase = { phase -> uiState.update { it.copy(capturePhase = phase) } },
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
                            uiState.update {
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
                        uiState.update { it.copy(error = e.safeUserMessage()) }
                    }
                }
            } finally {
                uiState.update { it.copy(isCapturing = false, capturePhase = null) }
                isProxyLaunched.set(false)
            }
        }
    }

    fun exportUIGF(version: String) {
        val snapshot = uiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            UigfExporter.export(uid to records, version)
                .onSuccess { path ->
                    uiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") }
                    LogWriter.instance.tryLog(LogBody(Severity.INFO, "UIGF export: ignoreThreeStar is always false for UIGF format, exported to $path"))
                }
                .onFailure { e ->
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "UIGF export failed", e))
                    uiState.update { it.copy(error = e.message) }
                }
        }
    }

    fun exportExcel() {
        val snapshot = uiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            runCatching {
                ExcelWriter.exportExcel(mapOf(uid to records))
            }.onSuccess {
                uiState.update { it.copy(message = I18nManager["message.export_success"]) }
                LogWriter.instance.tryLog(LogBody(Severity.INFO, "Excel exported for uid=$uid"))
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Excel export failed", e))
                uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun exportCsv() {
        val snapshot = uiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            CsvExporter.export(records, uid, ignoreThreeStar = snapshot.ignoreThreeStarExport)
                .onSuccess { path -> uiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") } }
                .onFailure { e -> uiState.update { it.copy(error = e.message) } }
        }
    }

    fun exportHtml() {
        val snapshot = uiState.value
        val uid = snapshot.selectedUid ?: return
        val records = snapshot.records
        scope.launch(Dispatchers.IO) {
            HtmlExporter.export(records, uid, ignoreThreeStar = snapshot.ignoreThreeStarExport)
                .onSuccess { path -> uiState.update { it.copy(message = I18nManager["message.export_success"] + " → $path") } }
                .onFailure { e -> uiState.update { it.copy(error = e.message) } }
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
                val current = uiState.value
                val updated = transform(current)
                PreferencesManager.saveUserPreferences(
                    updated.themeMode, updated.language, updated.showAllItems, updated.ignoreThreeStarExport,
                )
                // Preference persistence wraps failures, including cancellation, in Result.
                currentCoroutineContext().ensureActive()
                if (updated.language != current.language) I18nManager.load(updated.language)
                uiState.update {
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
        val uid = uiState.value.selectedUid ?: return
        scope.launch {
            Database.delete(uid).onSuccess {
                refreshUidList()
                val next = uiState.value.uids.firstOrNull()
                if (next != null) selectUid(next)
                else {
                    ++accountRevision
                    ++selectionRequest
                    selectionJob?.cancel()
                    uiState.update {
                        it.nullAllData().copy(selectedUid = null, isLoading = false)
                    }
                }
            }.onFailure { e ->
                LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to delete uid=$uid", e))
                uiState.update { it.copy(error = e.message) }
            }
        }
    }

    private suspend fun refreshUidList() {
        val uids = Database.listAllUids().getOrDefault(emptyList())
        uiState.update { it.copy(uids = uids) }
        if (uiState.value.selectedUid == null && uids.isNotEmpty()) {
            startUidLoad(uids.first())
        }
    }

    // Load account from the database
    private suspend fun loadUid(uid: String): Result<LoadedAccount> =
        withContext(Dispatchers.IO) {
            val result = searchAccount(uid)
            currentCoroutineContext().ensureActive()
            result.fold(
                onSuccess = { data -> computeAccount(data?.first ?: emptyList(), data?.second, today()) },
                onFailure = { Result.failure(it) },
            )
        }

    // Suspended function to compute the statistics, then cached.
    private suspend fun computeAccount(
        records: List<GachaRecord>, stats: UserStatistics?, day: LocalDate,
    ): Result<LoadedAccount> = withContext(Dispatchers.Default) {
        runCatching {
            currentCoroutineContext().ensureActive()
            val analytics = CachedAnalytics(
                bannerStats = records.limitedBannerStats(), timeline = records.buildTimeline(),
                goldHistory = records.buildGoldHistory(), calendarDays = records.calendarDays(),
                longestNoPullDays = records.longestNoPullIntervalDays(day),
                limitedWeaponAvgPity = records.limitedWeaponAvgPity(), upRatio = records.upRatioCalculator(),
            )
            currentCoroutineContext().ensureActive()
            LoadedAccount(
                records = records, stats = stats,
                pityState = records.pityState(), luck = records.analyzeLuck(),
                consumption = records.monthlyConsumption(), streakAnalysis = records.analyzeStreaks(),
                analytics = analytics, day = day,
            )
        }
    }

    fun updateStats() {
        val uid = uiState.value.selectedUid ?: return
        scope.launch {
            val revision = accountRevision
            withContext(Dispatchers.IO) {
                Database.updateStats(uid).onSuccess {
                    withContext(Dispatchers.Main) {
                        if (accountRevision == revision && uiState.value.selectedUid == uid) startUidLoad(uid)
                    }
                }.onFailure { e ->
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to update stats for uid=$uid", e))
                }
            }
        }
    }

    fun updateRecords() {
        val uid = uiState.value.selectedUid ?: return

        scope.launch {
            val revision = accountRevision
            withContext(Dispatchers.IO) {
                Database.updateRecords(uid) { records ->
                    records.map { it.copy(itemID = ItemTranslator.getIdByName(it.name).ifBlank { it.itemID }) }
                }.onSuccess {
                    withContext(Dispatchers.Main) {
                        if (accountRevision == revision && uiState.value.selectedUid == uid) startUidLoad(uid)
                    }
                }.onFailure { e ->
                    LogWriter.instance.tryLog(LogBody(Severity.ERROR, "Failed to update records for uid=$uid", e))
                }
            }
        }
    }

}

private fun <T> Result<T>.getOrDefault(default: T): T = getOrElse { default }
