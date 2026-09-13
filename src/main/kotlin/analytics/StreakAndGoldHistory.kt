package analytics

import model.GachaRecord
import model.compareGachaRecordIds
import model.sortedChronologically
import utilities.AppConstants
import utilities.AppConstants.GuaranteeType
import utilities.AppConstants.Result

/**
 * 根据卡池类型与历史五星，判定本次五星所属的保底类型。
 * 允许计算大小保底和明光，集录和武器不计算
 * @param banner 卡池代码（合并后的角色池使用 301）
 * @param record 抽
 * @param hadFiveOnBanner 本池是否已出过五星
 * @param lastFiveWasLimited 上次五星是否为 UP（非常驻物品）
 * @return 对应的 [GuaranteeType]；非五星调用方应在外层过滤
 */
internal fun getFiveStarState ( banner: String,record: GachaRecord,
    hadFiveOnBanner: Boolean, lastFiveWasLimited: Boolean, currentLostStreak: Int = 0
): GuaranteeType {

    if (record.rankType != 5) return GuaranteeType.NONE
    if (banner == AppConstants.WEAPON_EVENT_BANNER || banner == AppConstants.CHRONICLE_EVENT_BANNER) return GuaranteeType.NONE

    return when {
        banner == AppConstants.STANDARD_EVENT_BANNER -> GuaranteeType.STANDARD
        (AppConstants.CharacterEventBannerSet.contains(banner)) && ExceptionalCharacters.getSetOfExceptionalCharacterID().contains(record.itemID) -> {
            checkIfExceptionalCharacterWasLimited(record, lastFiveWasLimited)
        }
        !AppConstants.StandardItemUID.contains(record.itemID) -> {
            if (hadFiveOnBanner && !lastFiveWasLimited) GuaranteeType.GUARANTEED
            else
                if (TimeCheckForCR.afterVer5(record.time) && currentLostStreak == 3) GuaranteeType.CAPTURE_RADIANCE
                else GuaranteeType.WON_FIFTY_FIFTY
        }
        currentLostStreak == 3 -> GuaranteeType.CAPTURE_RADIANCE
        hadFiveOnBanner && !lastFiveWasLimited -> GuaranteeType.GUARANTEED
        AppConstants.StandardItemUID.contains(record.itemID) -> GuaranteeType.LOST_FIFTY_FIFTY
        else -> GuaranteeType.STANDARD
    }
}

// Singleton object for checking the time of capture radiance
object TimeCheckForCR {
    const val CAPTURE_RADIANCE_START_TIME = "2024-08-28 06:00:00"
    fun afterVer5(recordTime: String): Boolean {
        return recordTime >= CAPTURE_RADIANCE_START_TIME
    }
}

// Analysis section for each banner.
data class StreakAnalysis(
    val maxConsecutiveUp: Int,
    val maxConsecutiveLoss: Int,
    val perBanner: Map<String, Pair<Int, Int>>,
)

/**
 * 统计各卡池连续不歪（UP）与连续歪的最长 streak。
 *
 * 角色活动池（301+400）合并计算；仅统计五星记录。
 *
 * @param banners 待分析的卡池 code 列表，默认角色活动 + 武器
 * @return [StreakAnalysis]，含全局与各池最长连 UP / 连歪
 */
fun List<GachaRecord>.analyzeStreaks(
    banners: Set<String> = setOf(AppConstants.CHARACTER_EVENT_BANNER, AppConstants.CHARACTER_EVENT_BANNER2)
): StreakAnalysis {
    var globalMaxUp = 0
    var globalMaxLoss = 0
    val perBanner = mutableMapOf<String, Pair<Int, Int>>()

    for (banner in banners) {
        var upStreak = 0
        var lossStreak = 0
        var maxUp = 0
        var maxLoss = 0
        var prevState: Result? = null

        val pool = AppConstants.resolveBannerPool(banner)

        for (record in filter { it.gachaType in pool }.sortedChronologically()) {
            if (record.rankType != 5) continue

            /** 只计入武器 + 角色， 剩下暂不计入，详情见 [shouldIgnore] */
            val state = if (shouldIgnore(banner)) {
                Result.IGNORE
            } else {
                val isUp = !AppConstants.StandardItemUID.contains(record.itemID)
                if (isUp && prevState == Result.LOSS) {
                    Result.GUARANTEED_UP// 大保底的金
                } else if (isUp) {
                    Result.UP// 上一个没歪，那么这次小保底拿下
                } else {
                    Result.LOSS// 歪
                }
            }

            when (state) {
                Result.LOSS -> {
                    lossStreak++
                    if (upStreak != 0) upStreak = 0
                    maxLoss = maxOf(maxLoss, lossStreak)
                }
                Result.UP -> {
                    upStreak++
                    if (lossStreak != 0) lossStreak = 0
                    maxUp = maxOf(maxUp, upStreak)
                }
                Result.GUARANTEED_UP -> {  }
                Result.IGNORE -> {  }
            }

            prevState = state
        }

        perBanner[banner] = maxUp to maxLoss
        globalMaxUp = maxOf(globalMaxUp, maxUp)
        globalMaxLoss = maxOf(globalMaxLoss, maxLoss)
    }

    return StreakAnalysis(globalMaxUp, globalMaxLoss, perBanner)
}

/**
 * 检查本次抽卡是否需要被忽略。
 * [Result.IGNORE]（四星、常驻、新手和集录祈愿）
 * 因为我们无法确定用户的定轨，所以无法分析是歪了还是赢了。
 */
private fun shouldIgnore (banner: String): Boolean {
    return when (banner) {
        //AppConstants.WEAPON_EVENT_BANNER,
        AppConstants.CHRONICLE_EVENT_BANNER,
        AppConstants.NOVICE_EVENT_BANNER,
        AppConstants.STANDARD_EVENT_BANNER -> true
        else -> false
    }
}

data class GoldPullSegment( val bannerCode: String, val pity: Int, val guaranteeType: GuaranteeType,
    val itemId: String, val time: String, val recordId: String = "",
) {
    val isStandardLoss: Boolean
        get() = guaranteeType == GuaranteeType.LOST_FIFTY_FIFTY || (bannerCode == AppConstants.WEAPON_EVENT_BANNER
                && guaranteeType == GuaranteeType.STANDARD)
}

/**
 * 按照各卡池构造出金历史图表。。
 *
 * @return map包含每个卡池以及出金历史，每个record按照时间重新进行倒叙排列。
 */
fun List<GachaRecord>.buildGoldHistory(): Map<String, List<GoldPullSegment>> {
    return goldHistoryBanners.associateWith { banner ->
        buildGoldHistoryForBannerPool(AppConstants.resolveBannerPool(banner), banner)
            .sortedWith { a, b ->
                compareGachaRecordIds(b.recordId, a.recordId).let {
                    if (it != 0) it else b.time.compareTo(a.time) }
            }
    }
}
// 构建一个列表，包含了需要处理的卡池
val goldHistoryBanners = listOf(AppConstants.CHARACTER_EVENT_BANNER, AppConstants.WEAPON_EVENT_BANNER, AppConstants.CHRONICLE_EVENT_BANNER, AppConstants.STANDARD_EVENT_BANNER)

/**
 * 基于 gacha_type的list集合，按统一时间线计算每次五星的垫刀与保底类型。
 *
 * 角色活动池（banner.301 / banner.400）合并；武器池不标注保底。
 *
 * @param gachaTypes 实际纳入统计的 gacha_type 集合
 * @param displayBanner 展示用卡池 code（写入 [GoldPullSegment.bannerCode]）
 * @return 该池所有五星
 */
private fun List<GachaRecord>.buildGoldHistoryForBannerPool(gachaTypes: Set<String>, displayBanner: String,
): List<GoldPullSegment> {
    val segments = mutableListOf<GoldPullSegment>()
    var pity = 0
    var lastFiveWasLimited = false
    var hadFiveOnBanner = false
    var losingStreak = 0

    for (record in filter { it.gachaType in gachaTypes }.sortedChronologically()) {
        pity++

        if (record.rankType == 5) {
            val guarantee = if (record.gachaType == AppConstants.WEAPON_EVENT_BANNER) {
                GuaranteeType.NONE
            } else {
                getFiveStarState(record.gachaType, record, hadFiveOnBanner, lastFiveWasLimited, losingStreak)
            }

            segments.add(GoldPullSegment(
                    bannerCode = displayBanner,
                    pity = pity,
                    guaranteeType = guarantee,
                    itemId = record.itemID,
                    time = record.time,
                    recordId = record.recordID,
                ))

            if (record.gachaType == AppConstants.CHARACTER_EVENT_BANNER || record.gachaType == AppConstants.CHARACTER_EVENT_BANNER2) {
                hadFiveOnBanner = true
                lastFiveWasLimited = !AppConstants.StandardItemUID.contains(record.itemID)
                losingStreak = losingStreakCounter(lastFiveWasLimited, losingStreak)
            }
            pity = 0
        }
    }
    return segments
}

// 定义一个数据类为日历图标使用
data class CalendarItem( val itemId: String, val rankType: Int, val itemType: String, val gachaType: String)

// 数据类包含每天的抽数，花费，获得五星四星物品等。
data class CalendarDay( val date: String, val pullCount: Int, val fiveStars: Int, val fourStars: Int,
    val items: List<CalendarItem> = emptyList(),
) {
    val primogems: Int get() = pullCount * 160
}

/**
 * 按日期（格式：yyyy-MM-dd）来聚合所有的抽卡记录以供日历页图表展示。
 *
 * 每日统计抽数、四星和五星数量、原石花费和获得物品；每日的记录按 id 时序排列。
 *
 * @return map包含日期字符串 → [CalendarDay]
 */
fun List<GachaRecord>.calendarDays(): Map<String, CalendarDay> {
    // 确保日期string合法，格式：yyyy-mm-dd，然后聚合每日的所有抽数并创建一个新列表
    return filter { it.time.length >= 10 }.groupBy { it.time.substring(0, 10) }
        // 值转换，我们解构map里的key
        .mapValues { (_, records) ->
        val sorted = records.sortedChronologically()
        CalendarDay(
            date = sorted.first().time.substring(0, 10),
            pullCount = sorted.size,
            fiveStars = sorted.count { it.rankType == 5 },
            fourStars = sorted.count { it.rankType == 4 },// 四星五星筛选
            items = sorted.map { record ->
                CalendarItem( itemId = record.itemID, rankType = record.rankType,
                    itemType = record.itemType, gachaType = record.gachaType
                )
            }
        )
    }
}
