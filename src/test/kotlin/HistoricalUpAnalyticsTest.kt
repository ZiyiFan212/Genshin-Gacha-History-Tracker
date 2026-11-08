import analytics.*
import model.GachaRecord
import model.GuaranteeType
import utilities.AppConstants
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoricalUpAnalyticsTest {
    private fun pull(id: Int, item: String, time: String, banner: String = "301") =
        GachaRecord(banner, time, "Test", "character", item, id.toString(), 5)

    private val periods = listOf(
        Triple("10000042", "2021-02-17 18:00:00", "2021-03-02 15:59:59"),
        Triple("10000069", "2022-08-24 06:00:00", "2022-09-09 17:59:59"),
        Triple("10000079", "2023-03-01 06:00:00", "2023-03-21 17:59:59"),
        Triple("10000109", "2025-05-14 06:00:00", "2025-06-03 17:59:59"),
    )

    @Test fun `historical UP agrees across timeline win rate streak counts and pity`() {
        for ((item, start, end) in periods) {
            val records = listOf(pull(1, item, start), pull(2, item, end, "400"))
            assertEquals(listOf(GuaranteeType.WON_FIFTY_FIFTY, GuaranteeType.WON_FIFTY_FIFTY),
                records.buildTimeline().map { it.guaranteeType }, item)
            assertEquals(100.0, records.calculateWinRate(AppConstants.CharacterEventBannerSet), item)
            val streaks = records.analyzeStreaks()
            assertEquals(2, streaks.maxConsecutiveUp, item)
            assertEquals(0, streaks.maxConsecutiveLoss, item)
            assertEquals(2, records.calculateStat().fiveStarCharacterLim, item)
            assertEquals(1.0, records.bannerStats("301").avgPity, item)
        }
    }

    @Test fun `historical UP after loss consumes guarantee consistently`() {
        for ((item, start, _) in periods) {
            val records = listOf(pull(1, "10000003", start), pull(2, item, start, "400"),
                pull(3, "10000089", start))
            assertEquals(listOf(GuaranteeType.WON_FIFTY_FIFTY, GuaranteeType.GUARANTEED,
                GuaranteeType.LOST_FIFTY_FIFTY), records.buildTimeline().map { it.guaranteeType }, item)
            assertEquals(50.0, records.calculateWinRate(AppConstants.CharacterEventBannerSet), item)
            assertEquals(1, records.analyzeStreaks().maxConsecutiveUp, item)
            assertEquals(1, records.analyzeStreaks().maxConsecutiveLoss, item)
            assertEquals(1.5, records.bannerStats("301").avgPity, item)
        }
    }

    @Test fun `outside historical period exceptional characters still count as losses`() {
        val records = periods.mapIndexed { index, (item, _, _) -> pull(index + 1, item, "2026-01-01 12:00:00") }
        assertEquals(0.0, records.calculateWinRate(AppConstants.CharacterEventBannerSet))
        assertEquals(4, records.analyzeStreaks().maxConsecutiveLoss)
        assertEquals(0, records.calculateStat().fiveStarCharacterLim)
    }

    @Test fun `historical UP period never makes standard pool records limited`() {
        for ((item, start, _) in periods) {
            val records = listOf(pull(1, item, start, "200"))
            assertEquals(0, records.calculateStat().fiveStarCharacterLim, item)
            assertEquals(GuaranteeType.STANDARD,
                records.buildTimeline(setOf("200")).single().guaranteeType, item)
        }
    }

    @Test fun `CN local time conversion matches resource epoch boundaries`() {
        // upTime.json: weapon 11501 has epoch interval 1600099200000..1605024000000.
        // These UTC+8 wall times verify the actual private conversion through its public caller.
        for (time in listOf("2020-09-15 00:00:00", "2020-11-11 00:00:00")) {
            assertEquals(100.0 to 0.0,
                listOf(pull(1, "11501", time, "302").copy(itemType = "weapon")).upRatioCalculator(), time)
        }
        for (time in listOf("2020-09-14 23:59:59", "2020-11-11 00:00:01")) {
            assertEquals(0.0 to 100.0,
                listOf(pull(1, "11501", time, "302").copy(itemType = "weapon")).upRatioCalculator(), time)
        }
    }
}
