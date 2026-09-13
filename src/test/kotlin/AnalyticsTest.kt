import utilities.AppConstants.GuaranteeType
import analytics.analyzeLuck
import analytics.analyzeStreaks
import analytics.bannerStats
import analytics.buildGoldHistory
import analytics.buildTimeline
import analytics.calendarDays
import analytics.currentPity
import analytics.monthlyConsumption
import analytics.pityState
import backup.BackupManager
import storage.CsvExporter
import storage.HtmlExporter
import kotlinx.coroutines.runBlocking
import model.GachaRecord
import utilities.AppBootstrap
import validation.DataValidator
import validation.Severity
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnalyticsTest {

    private fun record(
        gacha: String,
        time: String,
        rank: Int,
        itemId: String = "10000003",
        itemType: String = "character",
        id: String = "${gacha}_$time",
    ) = GachaRecord(gacha, time, "Test", itemType, itemId, id, rank)

    @Test
    fun `current pity counts pulls since last five star`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 3),
            record("301", "2024-01-02 12:00:00", 3),
            record("301", "2024-01-03 12:00:00", 5, "10000089"),
            record("301", "2024-01-04 12:00:00", 3),
            record("301", "2024-01-05 12:00:00", 4),
        )
        assertEquals(2, records.currentPity("301"))
    }

    @Test
    fun `pity state tracks all limited banners`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 3),
            record("302", "2024-01-02 12:00:00", 5, "11502", "weapon"),
        )
        val state = records.pityState()
        assertEquals(1, state.banner301)
        assertEquals(0, state.banner302)
    }

    @Test
    fun `banner stats calculates win rate and avg pity`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 3),
            record("301", "2024-01-02 12:00:00", 5, "10000089"),
        )
        val stats = records.bannerStats("301")
        assertEquals(2, stats.totalWishes)
        assertEquals(1, stats.fiveStars)
        assertEquals(100.0, stats.winRate)
        assertEquals(2.0, stats.avgPity)
    }

    @Test
    fun `timeline marks fifty fifty outcomes`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 5, "10000089"),
            record("301", "2024-01-02 12:00:00", 5, "10000003"),
        )
        val timeline = records.buildTimeline()
        assertEquals(2, timeline.size)
        assertEquals(GuaranteeType.WON_FIFTY_FIFTY, timeline[1].guaranteeType)
        assertEquals(GuaranteeType.LOST_FIFTY_FIFTY, timeline[0].guaranteeType)
    }

    @Test
    fun `luck analysis returns summary`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 5, "10000089"),
        )
        val luck = records.analyzeLuck()
        assertTrue(luck.summary.isNotBlank())
    }

    @Test
    fun `monthly consumption groups by month`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 3),
            record("301", "2024-01-15 12:00:00", 3),
            record("301", "2024-02-01 12:00:00", 3),
        )
        val consumption = records.monthlyConsumption()
        assertEquals(2, consumption.size)
        assertEquals(2, consumption.find { it.month == "2024-01" }?.wishes)
    }

    @Test
    fun `streak analysis tracks consecutive up and loss`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 5, "10000089"),
            record("301", "2024-01-02 12:00:00", 5, "10000089"),
            record("301", "2024-01-03 12:00:00", 5, "10000003"),
            record("301", "2024-01-04 12:00:00", 5, "10000003"),
        )
        val streak = records.analyzeStreaks()
        assertEquals(2, streak.maxConsecutiveUp)
        assertEquals(2, streak.maxConsecutiveLoss)
    }

    @Test
    fun `gold history includes all banner pools sorted newest first`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 5, "10000089"),
            record("301", "2024-02-01 12:00:00", 5, "10000003"),
            record("302", "2024-01-02 12:00:00", 5, "11502", "weapon"),
            record("400", "2024-01-03 12:00:00", 5, "10000003"),
            record("500", "2024-01-04 12:00:00", 5, "10000089"),
            record("200", "2024-01-05 12:00:00", 5, "10000003"),
        )
        val history = records.buildGoldHistory()
        assertEquals(4, history.size)
        assertTrue(history.containsKey("302"))
        assertEquals(1, history["200"]?.size)
        assertTrue(history["500"].orEmpty().isNotEmpty())
        assertFalse(history.containsKey("400"))
        assertEquals(3, history["301"]?.size)
        assertTrue(history["301"].orEmpty().any { it.time == "2024-01-03 12:00:00" })
        assertTrue(history["301"].orEmpty().any { it.guaranteeType == GuaranteeType.LOST_FIFTY_FIFTY })
    }

    @Test
    fun `character event pity and guarantee are shared across 301 and 400`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 3),
            record("301", "2024-01-02 12:00:00", 3),
            record("400", "2024-01-03 12:00:00", 5, "10000089"),
            record("400", "2024-01-04 12:00:00", 3),
        )
        assertEquals(1, records.currentPity("301"))
        assertEquals(1, records.currentPity("400"))
        val fiveStar = records.buildGoldHistory()["301"]!!.single()
        assertEquals(3, fiveStar.pity)
        assertEquals(GuaranteeType.WON_FIFTY_FIFTY, fiveStar.guaranteeType)
    }

    @Test
    fun `pity uses record id order when timestamps match`() {
        val sameTime = "2026-01-14 10:48:56"
        val records = listOf(
            record("301", sameTime, 5, "10000109", id = "1768357200073375202"),
            record("301", sameTime, 4, "10000113", id = "1768357200073375102"),
            record("301", sameTime, 5, "10000125", id = "1768357200073375002"),
        )
        val history = records.buildGoldHistory()["301"].orEmpty()
        assertEquals(2, history.size)
        assertEquals(2, history[0].pity)
        assertEquals(1, history[1].pity)
    }

    @Test
    fun `calendar groups pulls by date`() {
        val records = listOf(
            record("301", "2024-01-01 12:00:00", 5, "10000089"),
            record("301", "2024-01-01 13:00:00", 3),
        )
        val days = records.calendarDays()
        assertEquals(2, days["2024-01-01"]?.pullCount)
        assertEquals(1, days["2024-01-01"]?.fiveStars)
    }
}

class ValidationExportTest {

    init {
        runBlocking { AppBootstrap.initialize("en").getOrThrow() }
    }

    private val sample = GachaRecord(
        "301", "2024-01-01 12:00:00", "Jean", "character", "10000003", "1", 5
    )

    @Test
    fun `validator detects duplicate ids`() {
        val report = DataValidator.validate(listOf(sample, sample.copy(name = "Dup")), "123")
        assertTrue(report.hasErrors)
        assertTrue(report.issues.any { it.severity == Severity.ERROR })
    }

    @Test
    fun `csv export creates file`() {
        val dir = createTempDirectory("csv-test")
        val result = CsvExporter.export(listOf(sample), "123", dir)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().toFile().exists())
    }

    @Test
    fun `html export creates file`() {
        val dir = createTempDirectory("html-test")
        val result = HtmlExporter.export(listOf(sample), "123", dir)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().toFile().readText().contains("2024-01-01"))
    }

    @Test
    fun `backup list returns empty when no db`() {
        val backups = BackupManager.listBackups()
        assertTrue(backups.isSuccess)
    }
}
