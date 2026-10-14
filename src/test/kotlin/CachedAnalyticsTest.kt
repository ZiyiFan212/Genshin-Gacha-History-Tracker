import analytics.*
import assets.UpTimeLoader
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import model.GachaRecord
import ui.AppScreen
import ui.AppUiState
import ui.AppViewModel
import java.time.LocalDate
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.*

class CachedAnalyticsTest {
    private fun records(count: Int) = List(count) { index ->
        GachaRecord("301", "2026-01-01 12:00:00", "Test", "character", "10000003", (index + 1).toString(), 3)
    }

    private suspend fun ready(viewModel: AppViewModel, uid: String): AppUiState = withTimeout(10_000) {
        viewModel.state.first { it.selectedUid == uid && !it.isLoading && it.analyticsDate != null }
    }

    @Test
    fun `background results publish together and screen entry refresh preserves other state`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val source = records(3)
        val day = LocalDate.of(2026, 1, 10)
        val searchStarted = CompletableDeferred<Unit>()
        val releaseSearch = CompletableDeferred<Unit>()
        val viewModel = AppViewModel(scope, {
            assertFalse(javax.swing.SwingUtilities.isEventDispatchThread())
            searchStarted.complete(Unit)
            releaseSearch.await()
            Result.success(source to source.calculateStat())
        }, { day })
        try {
            viewModel.selectUid("A")
            searchStarted.await()
            assertTrue(viewModel.state.value.isLoading)
            assertTrue(viewModel.state.value.records.isEmpty())
            assertTrue(viewModel.state.value.analytics.timeline.isEmpty())
            releaseSearch.complete(Unit)
            val loaded = ready(viewModel, "A")
            assertSame(source, loaded.records)
            assertEquals(source.buildTimeline(), loaded.analytics.timeline)
            assertEquals(source.limitedBannerStats(), loaded.analytics.bannerStats)
            assertEquals(source.buildGoldHistory(), loaded.analytics.goldHistory)
            assertEquals(source.calendarDays(), loaded.analytics.calendarDays)
            assertEquals(source.longestNoPullIntervalDays(day), loaded.analytics.longestNoPullDays)
            assertEquals(source.limitedWeaponAvgPity(), loaded.analytics.limitedWeaponAvgPity)
            assertEquals(source.upRatioCalculator(), loaded.analytics.upRatio)
            withContext(Dispatchers.Main) {
                viewModel.navigate(AppScreen.STATS)
                assertTrue(viewModel.state.value.isLoading)
            }
            val refreshed = ready(viewModel, "A")
            assertEquals(AppScreen.STATS, refreshed.currentScreen)
            assertSame(source, refreshed.records)
            assertEquals(loaded.analytics, refreshed.analytics)
            assertNotSame(loaded.analytics, refreshed.analytics)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `server date refresh recomputes without reloading records`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val source = records(1)
        val day = AtomicReference(LocalDate.of(2026, 1, 10))
        var reads = 0
        val viewModel = AppViewModel(scope, { reads++; Result.success(source to source.calculateStat()) }, day::get)
        try {
            viewModel.selectUid("A")
            val loaded = ready(viewModel, "A")
            withContext(Dispatchers.Main) { viewModel.refreshAnalytics(dateChanged = true) }
            assertSame(loaded.analytics, viewModel.state.value.analytics)
            day.set(day.get().plusDays(1))
            withContext(Dispatchers.Main) {
                viewModel.refreshAnalytics(dateChanged = true)
                assertTrue(viewModel.state.value.isLoading)
            }
            val refreshed = ready(viewModel, "A")
            assertEquals(loaded.analytics.longestNoPullDays + 1, refreshed.analytics.longestNoPullDays)
            assertEquals(day.get(), refreshed.analyticsDate)
            assertSame(source, refreshed.records)
            assertEquals(1, reads)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `late first A cannot overwrite newer A records or analytics`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstFinished = CompletableDeferred<Unit>()
        var calls = 0
        val fresh = records(2)
        val viewModel = AppViewModel(scope, {
            if (++calls == 1) {
                firstStarted.complete(Unit)
                withContext(NonCancellable) { releaseFirst.await() }
                firstFinished.complete(Unit)
                val old = records(1)
                Result.success(old to old.calculateStat())
            } else Result.success(fresh to fresh.calculateStat())
        })
        try {
            viewModel.selectUid("A")
            firstStarted.await()
            withContext(Dispatchers.Main) {
                viewModel.selectUid("B")
                viewModel.selectUid("A")
            }
            val loaded = ready(viewModel, "A")
            releaseFirst.complete(Unit)
            firstFinished.await()
            withContext(Dispatchers.Main) { yield() }
            assertSame(fresh, loaded.records)
            assertEquals(2, loaded.analytics.timeline.size)
            assertSame(loaded.analytics, viewModel.state.value.analytics)
        } finally {
            releaseFirst.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun `concurrent resource loads reuse one published map`() {
        val executor = Executors.newFixedThreadPool(4)
        try {
            val maps = executor.invokeAll(List(20) {
                Callable {
                    UpTimeLoader.load().getOrThrow()
                    UpTimeLoader.getAllEntries()
                }
            }).map { it.get() }
            assertTrue(maps.first().isNotEmpty())
            maps.forEach { assertSame(maps.first(), it) }
        } finally {
            executor.shutdownNow()
        }
    }
}
