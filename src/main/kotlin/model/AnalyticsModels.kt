package model

import utilities.AppConstants

/**
 * Enum class to define different guarantee types.
 *
 * - [WON_FIFTY_FIFTY]: Winning a 50/50.
 * - [LOST_FIFTY_FIFTY]: Losing a 50/50.
 * - [CAPTURE_RADIANCE]: Guaranteed pity after losing 50/50 three times.
 * - [GUARANTEED]: Guaranteed pity after losing a 50/50.
 * - [STANDARD]: Non-character event banner five-star items are marked as this.
 * - [NONE]: Four-star items.
 */
enum class GuaranteeType {
    WON_FIFTY_FIFTY,
    LOST_FIFTY_FIFTY,
    CAPTURE_RADIANCE,
    GUARANTEED,
    STANDARD,
    NONE,
}

/**
 * Helper enum class to describe the status of every pull.
 *
 * - [UP]: Winning a limited five-star item, last was not guaranteed pity.
 * - [LOSS]: Losing a 50/50.
 * - [IGNORE]: Not counted banner and four-star items.
 * - [GUARANTEED_UP]: The guaranteed five-star limited item after losing the 50/50
 */
enum class PullOutcome {
    UP,
    LOSS,
    IGNORE,
    GUARANTEED_UP,
}

// stat for each banner
data class BannerStats( val bannerCode: String, val totalWishes: Int, val fourStars: Int, val fiveStars: Int,
    val winRate: Double, val avgPity: Double, val currentPity: Int, val totalPity: Int)

data class PityState(
    val banner301: Int,
    val banner302: Int,
    val banner400: Int,
    val banner500: Int,
    val banner200: Int
)

// each record's current pulls and state
data class TimelineEntry(val record: GachaRecord, val pityAtPull: Int, val guaranteeType: GuaranteeType)

data class MonthlyConsumption(val month: String, val wishes: Int, val primogems: Int)

data class LuckAnalysis( val avgPity: Double, val winRate: Double, val theoreticalAvgPity: Double, val pityLuckScore: Double,
    val theoreticalWinRate: Double, val winLuckScore: Double, val summary: String)

data class StreakAnalysis(
    val maxConsecutiveUp: Int,
    val maxConsecutiveLoss: Int,
    val perBanner: Map<String, Pair<Int, Int>>,
)

data class GoldPullSegment( val bannerCode: String, val pity: Int, val guaranteeType: GuaranteeType,
    val itemId: String, val time: String, val recordId: String = "",
) {
    val isStandardLoss: Boolean
        get() = guaranteeType == GuaranteeType.LOST_FIFTY_FIFTY || (bannerCode == AppConstants.WEAPON_EVENT_BANNER
                && guaranteeType == GuaranteeType.STANDARD)
}

data class CalendarItem( val itemId: String, val rankType: Int, val itemType: String, val gachaType: String)

// everyday's consumption
data class CalendarDay( val date: String, val pullCount: Int, val fiveStars: Int, val fourStars: Int,
    val items: List<CalendarItem> = emptyList(),
) {
    val primogems: Int get() = pullCount * 160
}

