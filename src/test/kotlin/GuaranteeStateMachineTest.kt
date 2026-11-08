package analytics

import model.GachaRecord
import model.GuaranteeType
import model.GuaranteeType.*
import kotlin.test.Test
import kotlin.test.assertEquals

class GuaranteeStateMachineTest {
    private val Jean_C = "10000003"
    private val Dehya_UP = "10000089"

    private fun pull(id: Int, item: String = Dehya_UP, time: String = "2025-01-01 12:00:00",
                     banner: String = "301", rank: Int = 5) =
        GachaRecord(banner, time, "Test", "character", item, id.toString(), rank)

    // Exercise both callers: classification alone cannot catch incorrect state updates.
    private fun assertHistory(records: List<GachaRecord>, vararg expected: GuaranteeType) {
        val input = records.reversed() // Imports need not arrive in chronological order.
        val timeline = input.buildTimeline(setOf("301", "400"))
            .filter { it.record.rankType == 5 }.associate { it.record.recordID to it.guaranteeType }
        val gold = input.buildGoldHistory().getValue("301").associate { it.recordId to it.guaranteeType }
        val ids = records.filter { it.rankType == 5 }.map { it.recordID }
        assertEquals(
            mapOf("timeline" to expected.toList(), "gold history" to expected.toList()),
            mapOf("timeline" to ids.map { timeline[it] }, "gold history" to ids.map { gold[it] }),
        )
    }

    @Test fun `first standard, limited result test`() {
        assertHistory(listOf(pull(1, Jean_C)), LOST_FIFTY_FIFTY)
        assertHistory(listOf(pull(1)), WON_FIFTY_FIFTY)
    }

    @Test fun `loss guarantees next UP across character banners and lower ranks`() {
        assertHistory(listOf(pull(1, Jean_C), pull(2, rank = 4),
            pull(3, banner = "400"), pull(4)),
            LOST_FIFTY_FIFTY, GUARANTEED, WON_FIFTY_FIFTY)
    }



    /**
     * Edge case: There only exist one character, and it is exceptional (used to be limited).
     */
    @Test fun `only one exceptional character in the list, expected result is win`() {
        val cases = listOf("10000042" to "2021-02-18 12:00:00",
            "10000069" to "2022-08-25 12:00:00",
            "10000079" to "2023-03-02 12:00:00",
            "10000109" to "2025-05-15 12:00:00")
        for ((item, time) in cases) assertHistory(listOf(pull(1, item, time)), WON_FIFTY_FIFTY)
    }
    @Test fun `first exceptional character outside UP period is a loss`() {
        for (item in ExceptionalCharacters.getSetOfExceptionalCharacterID()) {
            assertHistory(listOf(pull(1, item, "2026-01-01 12:00:00")), LOST_FIFTY_FIFTY)
        }
    }

    @Test fun `exceptional UP consumes guarantee and leaves next UP on fifty fifty`() {
        assertHistory(listOf(pull(1, Jean_C, "2023-03-02 12:00:00"),
            pull(2, "10000079", "2023-03-02 12:00:00"),
            pull(3, time = "2023-03-02 12:00:00")),
            LOST_FIFTY_FIFTY, GUARANTEED, WON_FIFTY_FIFTY)
    }


    /**
     * All capture radiance tests.
     * Tests follow the rule of updating losing streak from the obtained result.
     */
    @Test fun `CR classification with three accumulated losses after version five`() {
        assertEquals(CAPTURE_RADIANCE,
            getFiveStarState("301", pull(1), true, true, 3))
    }
    // All seven records are after 5.0.
    // Guarantee results must preserve accumulated losses.
    /** Lost->Guaranteed, repeated three times, the seventh should be CR */
    @Test fun `three losses alternating with guarantees lead to CR in both callers`() {
        assertHistory(listOf(pull(1, Jean_C), pull(2), pull(3, Jean_C),
            pull(4), pull(5, Jean_C), pull(6), pull(7)),
            LOST_FIFTY_FIFTY, GUARANTEED, LOST_FIFTY_FIFTY, GUARANTEED,
            LOST_FIFTY_FIFTY, GUARANTEED, CAPTURE_RADIANCE)
    }

    @Test fun `CR resets counter before next cycle and shared banners preserve losses`() {
        val records = (1..2).flatMap { cycle ->
            val base = (cycle - 1) * 7
            (1..7).map { index -> pull(base + index,
                if (index in listOf(1, 3, 5)) Jean_C else Dehya_UP,
                banner = if (index % 2 == 0) "400" else "301") }
        }
        val cycle = listOf(LOST_FIFTY_FIFTY, GUARANTEED, LOST_FIFTY_FIFTY,
            GUARANTEED, LOST_FIFTY_FIFTY, GUARANTEED, CAPTURE_RADIANCE)
        assertHistory(records, *(cycle + cycle).toTypedArray())
    }

    @Test fun `exceptional UP supports both guaranteed and CR positions`() {
        val time = "2025-05-15 12:00:00"
        assertHistory((1..8).map { id -> pull(id,
            when (id) {
                1, 3, 5 -> Jean_C
                6, 7 -> "10000109"
                else -> Dehya_UP
            }, time) },
            LOST_FIFTY_FIFTY, GUARANTEED, LOST_FIFTY_FIFTY, GUARANTEED,
            LOST_FIFTY_FIFTY, GUARANTEED, CAPTURE_RADIANCE, WON_FIFTY_FIFTY)
    }

    @Test fun `counter supplies zero one two for consecutive loss inputs`() {
        var streak = 0
        val passed = mutableListOf<Int>()
        repeat(3) {
            passed.add(streak)
            streak = losingStreakCounter(false, streak)
        }
        assertEquals(listOf(0, 1, 2), passed)
    }

    @Test fun `ordinary fifty fifty win resets loss streak`() {
        assertHistory(listOf(pull(1, Jean_C), pull(2), pull(3),
            pull(4, Jean_C), pull(5), pull(6)),
            LOST_FIFTY_FIFTY, GUARANTEED, WON_FIFTY_FIFTY,
            LOST_FIFTY_FIFTY, GUARANTEED, WON_FIFTY_FIFTY)
    }

    /**
     * Edge case: We follow the rule that the streak only accumulates after version 5.0
     */
    @Test fun `pre version five losses do not count for CR after version five`() {
        assertHistory(listOf(pull(1, Jean_C, "2024-08-27 12:00:00"),
            pull(2, time = "2024-08-27 12:00:00"),
            pull(3, Jean_C, "2024-08-27 12:00:00"),
            pull(4, time = "2024-08-27 12:00:00"),
            pull(5, Jean_C), pull(6), pull(7)),
            LOST_FIFTY_FIFTY, GUARANTEED, LOST_FIFTY_FIFTY, GUARANTEED,
            LOST_FIFTY_FIFTY, GUARANTEED, WON_FIFTY_FIFTY)
    }
    @Test fun `pre version five loss preserves guarantee across version boundary`() {
        assertHistory(listOf(pull(1, Jean_C, "2024-08-28 05:59:59"),
            pull(2, time = "2024-08-28 06:00:00"), pull(3)),
            LOST_FIFTY_FIFTY, GUARANTEED, WON_FIFTY_FIFTY)
    }

    @Test fun `only three post version five losses seed CR even after two earlier losses`() {
        val records = (1..11).map { id -> pull(id,
            if (id in listOf(1, 3, 5, 7, 9)) Jean_C else Dehya_UP,
            if (id <= 4) "2024-08-27 12:00:00" else "2024-08-28 06:00:00") }
        assertHistory(records,
            LOST_FIFTY_FIFTY, GUARANTEED, LOST_FIFTY_FIFTY, GUARANTEED,
            LOST_FIFTY_FIFTY, GUARANTEED, LOST_FIFTY_FIFTY, GUARANTEED,
            LOST_FIFTY_FIFTY, GUARANTEED, CAPTURE_RADIANCE)
    }

    @Test fun `CR date boundary is inclusive`() {
        for ((time, expected) in listOf("2024-08-28 05:59:59" to WON_FIFTY_FIFTY,
            "2024-08-28 06:00:00" to CAPTURE_RADIANCE)) {
            assertEquals(expected, getFiveStarState("301", pull(1, time = time), true, true, 3), time)
        }
    }

    @Test fun `UP after one loss`() {
        assertEquals(GUARANTEED, getFiveStarState("301", pull(1), true, false, 1))
    }
    @Test fun `third loss guarantee is still guaranteed before next CR`() {
        assertEquals(GUARANTEED, getFiveStarState("301", pull(1), true, false, 3))
    }
    @Test fun `Lost 50-50 before CR threshold is a loss`() {
        assertEquals(LOST_FIFTY_FIFTY,
            getFiveStarState("301", pull(1, Jean_C), true, true, 1))
    }


    /**
     * All standard banner should be marked as 'Standard', and will not be affiliated with
     * any stat calculations.
     */
    @Test fun `standard banner remains standard regardless of character UP period or history`() {
        val records = listOf(pull(1, Jean_C, banner = "200"),
            pull(2, "10000109", "2025-05-15 12:00:00", banner = "200"))
        assertEquals(listOf(STANDARD, STANDARD),
            records.buildGoldHistory().getValue("200").map { it.guaranteeType })
        assertEquals(listOf(STANDARD, STANDARD),
            records.buildTimeline(setOf("200")).map { it.guaranteeType })
    }

    @Test fun `standard banner five star weapons are standard and lower ranks are none`() {
        val weapon = pull(1, "11502", banner = "200").copy(itemType = "weapon")
        assertEquals(STANDARD, getFiveStarState("200", weapon, false, false, 2))
        assertEquals(STANDARD, listOf(weapon).buildGoldHistory().getValue("200").single().guaranteeType)
        assertEquals(STANDARD, listOf(weapon).buildTimeline(setOf("200")).single().guaranteeType)
        for (rank in listOf(3, 4)) {
            assertEquals(NONE, getFiveStarState("200", weapon.copy(rankType = rank), false, false))
        }
    }

    @Test fun `excluded banners and lower ranks do not get character guarantee labels`() {
        for (banner in listOf("302", "500")) {
            assertEquals(NONE, getFiveStarState(banner, pull(1, banner = banner), true, false, 3))
        }
        assertEquals(NONE, getFiveStarState("301", pull(1, rank = 4), true, false, 3))
        assertEquals(STANDARD, getFiveStarState("200", pull(1, Jean_C, banner = "200"), false, false))
    }
}
