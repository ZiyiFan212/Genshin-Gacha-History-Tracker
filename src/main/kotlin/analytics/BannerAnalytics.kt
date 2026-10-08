package analytics

import model.BannerStats
import model.PityState
import model.TimelineEntry
import model.MonthlyConsumption
import model.LuckAnalysis

import model.GachaRecord
import utilities.records.compareChronologically
import utilities.records.sortedChronologically
import utilities.AppConstants
import model.GuaranteeType
import utilities.AppConstants.StandardItemUID
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private val limitedBanners = setOf(AppConstants.CHARACTER_EVENT_BANNER, AppConstants.CHARACTER_EVENT_BANNER2, AppConstants.WEAPON_EVENT_BANNER)

/**
 * 汇总武器和角色卡池的当前抽数，供首页展示。
 *
 * @return [PityState]；301和400合并为单一角色池，302武器。
 */
fun List<GachaRecord>.pityState(): PityState = PityState(
    banner301 = currentPity(AppConstants.CHARACTER_EVENT_BANNER),
    banner302 = currentPity(AppConstants.WEAPON_EVENT_BANNER),
    banner400 = currentPity(AppConstants.CHARACTER_EVENT_BANNER2),
    banner500 = currentPity(AppConstants.CHRONICLE_EVENT_BANNER),
    banner200 = currentPity(AppConstants.STANDARD_EVENT_BANNER)
)

/**
 * 卡池当前抽数
 *
 * 角色活动卡池，301和400，通过 [AppConstants.resolveBannerPool] 合并
 * 时间顺序排序后计算抽数，遇到五星清零直到算出当前抽数。
 *
 * @param banner 卡池 gacha_type
 * @return 当前抽数；无记录时返回 0
 */
fun List<GachaRecord>.currentPity(banner: String): Int {
    val pool = AppConstants.resolveBannerPool(banner)
    var pity = 0
    for (record in filter { it.gachaType in pool }.sortedChronologically()) {
        pity++
        if (record.rankType == 5) pity = 0
    }
    return pity
}

/**
 * 返回主要限定卡池的统计列表（角色活动合并池 + 武器池）。
 *
 * @return 角色活动（301）与武器（302）的 [BannerStats] 列表
 */
fun List<GachaRecord>.limitedBannerStats(): List<BannerStats> =
    listOf(AppConstants.CHARACTER_EVENT_BANNER, AppConstants.WEAPON_EVENT_BANNER).map { bannerStats(it) }

/**
 * 统计角色和武器卡池抽卡数据。
 *
 * 通过： [AppConstants.resolveBannerPool] 计算实际记录范围，再算总抽数等其他stats。
 * 301和400两个角色池统一归为: [AppConstants.CHARACTER_EVENT_BANNER]。
 *
 * @param banner 卡池： gacha_type
 * @return 汇总：[BannerStats]
 */
fun List<GachaRecord>.bannerStats(banner: String): BannerStats {
    val pool = AppConstants.resolveBannerPool(banner)
    val bannerCode = if (banner in AppConstants.CharacterEventBannerSet) AppConstants.CHARACTER_EVENT_BANNER else banner
    val bannerRecords = filter { it.gachaType in pool }
    val fiveStars = bannerRecords.filter { it.rankType == 5 }
    val fourStars = bannerRecords.count { it.rankType == 4 }
    val totalPityThisBanner = bannerRecords.size

    val winRate = when (bannerCode) {
        AppConstants.CHARACTER_EVENT_BANNER -> calculateWinRate(pool)
        else -> Double.NaN
    }

    val avgPity = if (bannerCode == AppConstants.CHARACTER_EVENT_BANNER) {
        averagePityCounter(pool, bannerRecords, AppConstants.StandardItemUID).average().let { if (it.isNaN()) 0.0 else it }
    } else {
        averagePityCounter(pool, bannerRecords).average().let { if (it.isNaN()) 0.0 else it }
    }

    return when (bannerCode) {
        // 角色卡池，计算胜率
        AppConstants.CHARACTER_EVENT_BANNER -> BannerStats(
            bannerCode = bannerCode,
            totalWishes = bannerRecords.size,
            fourStars = fourStars,
            fiveStars = fiveStars.size,
            winRate = winRate,
            avgPity = avgPity,
            currentPity = currentPity(bannerCode),
            totalPity = totalPityThisBanner
        )
        // 武器，胜率不计算
        AppConstants.WEAPON_EVENT_BANNER -> BannerStats(
            bannerCode = bannerCode,
            totalWishes = bannerRecords.size,
            fourStars = fourStars,
            fiveStars = fiveStars.size,
            winRate = Double.NaN,
            avgPity = avgPity,
            currentPity = currentPity(bannerCode),
            totalPity = totalPityThisBanner
        )
        // 剩下如果需要，支持修改
        else -> BannerStats(bannerCode, 0, 0, 0, 0.0, 0.0, 0, 0)
    }
}


/**
 * 构建限定卡池时间线，为每条记录标注出金时垫刀数与保底类型。
 *
 * 301 与 400 合并为同一 pity / 保底状态机；结果按抽卡 id 倒序排列。
 * 武器池（302）不标注保底类型。
 *
 * @param bannerFilter 参与时间线的 gacha_type 集合，默认 [limitedBanners]
 * @return 带垫刀与保底标注的 [TimelineEntry] 列表，最新在前
 */
fun List<GachaRecord>.buildTimeline(bannerFilter: Set<String> = limitedBanners): List<TimelineEntry> {
    val entries = mutableListOf<TimelineEntry>()
    // 根据卡池group所有的records
    val grouped = filter { it.gachaType in bannerFilter }.groupBy { record ->
        if (record.gachaType in AppConstants.CharacterEventBannerSet) AppConstants.CHARACTER_EVENT_BANNER
        else record.gachaType
    }

    for ((banner, records) in grouped) {
        var pity = 0
        var lastFiveWasLimited = false
        var hadFiveOnBanner = false
        var losingStreak = 0

        for (record in records.sortedChronologically()) {
            pity++
            val bannerType = record.gachaType
            var guarantee = GuaranteeType.NONE

            // 调用状态机确定本次五星是什么类型的保底
            if (record.rankType == 5) {
                guarantee = if (bannerType == AppConstants.WEAPON_EVENT_BANNER || bannerType == AppConstants.CHRONICLE_EVENT_BANNER) {// Double guarded
                    GuaranteeType.NONE
                } else {
                    getFiveStarState(banner, record, hadFiveOnBanner, lastFiveWasLimited, losingStreak)
                }

                if (bannerType == AppConstants.CHARACTER_EVENT_BANNER || bannerType == AppConstants.CHARACTER_EVENT_BANNER2) {
                    hadFiveOnBanner = true
                    lastFiveWasLimited = !AppConstants.StandardItemUID.contains(record.itemID)
                    losingStreak = losingStreakCounter(lastFiveWasLimited, losingStreak)
                }
            }

            entries.add(TimelineEntry(record, pity, guarantee))
            if (record.rankType == 5) {
                pity = 0
            }
        }
    }

    return entries.sortedWith { a, b -> b.record.compareChronologically(a.record) }// 时间排，修改原list
}

/**
 * 基于角色活动池统计结果，评估垫刀与不歪率相对理论值的运气指数。
 *
 * 理论平均垫刀 62，理论不歪率 50%，据此生成评分与文字总结。
 * ！判空检查！如果添加新的banner计算逻辑，请更改[limitedBannerStats]的 banner code！
 *
 * @return [LuckAnalysis] 含垫刀/不歪评分及 summary 标签
 */
fun List<GachaRecord>.analyzeLuck(): LuckAnalysis {
    val statsCharacter = limitedBannerStats().find { it.bannerCode == AppConstants.CHARACTER_EVENT_BANNER }!!
    val statsWeapon = limitedBannerStats().find { it.bannerCode == AppConstants.WEAPON_EVENT_BANNER }!!

    if (statsCharacter.fiveStars == 0) {
        return LuckAnalysis(0.0, 0.0, 0.0, 0.0, 50.0, 0.0, "stats.no_data")
    }

    val theoreticalAvgPity = 93.0
    val theoreticalWinRate = 50.0

    val avgPity = statsCharacter.avgPity
    val characterNumber = statsCharacter.fiveStars
    val weaponNumber = statsWeapon.fiveStars

    val pityLuckScore = when {
        avgPity == 0.0 -> 0.0
        avgPity <= theoreticalAvgPity -> ((theoreticalAvgPity - avgPity) / theoreticalAvgPity) * 100.0 * 0.6
        else -> ((theoreticalAvgPity - avgPity) / avgPity) * 100.0 * 0.6
    }

    val winRate = statsCharacter.winRate
    val winLuckScore = (winRate - theoreticalWinRate) * 1.2

    val totalFiveStars = characterNumber + weaponNumber
    val confidenceWeight = when {
        totalFiveStars >= 20 -> 1.0
        totalFiveStars >= 10 -> 0.8
        totalFiveStars >= 5 -> 0.6
        else -> 0.4
    }

    val totalScore = (pityLuckScore + winLuckScore) * confidenceWeight


    val summary = when {
        totalScore > 50 -> "stats.luck_extremely_lucky"
        totalScore > 30 -> "stats.luck_very_lucky"
        totalScore > 15 -> "stats.luck_above_average"
        totalScore > -15 -> "stats.luck_average"
        totalScore > -30 -> "stats.luck_below_average"
        totalScore > -50 -> "stats.luck_unlucky"
        else -> "stats.luck_extremely_unlucky"
    }

    return LuckAnalysis(avgPity, winRate, theoreticalAvgPity, pityLuckScore, theoreticalWinRate, winLuckScore, summary)
}
/**
 * 按自然月汇总全卡池抽数与原石消耗（每抽按 160 原石计）。
 *
 * @return 按月分组的 [MonthlyConsumption] 列表，月份倒序
 */
fun List<GachaRecord>.monthlyConsumption(): List<MonthlyConsumption> {
    return filter { it.gachaType in AppConstants.BannerCodeList.toSet() }
        .groupBy { it.time.substring(0, 7) }
        .map { (month, records) ->
            MonthlyConsumption(month, records.size, records.size * 160)
        }.sortedByDescending { it.month }
}

/**
 * If the list is zero, it returns 0; if the list is size 1, it returns the gap between the
 * pull date to the current date in an integer.
 * Otherwise, the gap is calculated through iterating the list.
 */
fun List<GachaRecord>.longestNoPullIntervalDays(): Int {
    if (this.isEmpty()) return 0

    // Handle if there's only one pull in the list.
    val today = LocalDate.now()
    if (this.size == 1){
        val pullDate = LocalDate.parse(this[0].time.substring(0, 10))
        return ChronoUnit.DAYS.between(pullDate, today).toInt()
    }

    val sorted = sortedChronologically()
    var maxGap = 0

    for (i in 1 until sorted.size) {
        val prev = LocalDate.parse(sorted[i - 1].time.substring(0, 10))
        val curr = LocalDate.parse(sorted[i].time.substring(0, 10))
        val gap = ChronoUnit.DAYS.between(prev, curr)
        if (gap > maxGap) maxGap = gap.toInt()
    }

    if (sorted.lastOrNull() != null) {
        val lastDate = LocalDate.parse(sorted.last().time.substring(0, 10))
        val lastGap = ChronoUnit.DAYS.between(lastDate, today)
        if (lastGap > maxGap) maxGap = lastGap.toInt()
    }
    return maxGap
}

/**
 * 计算限定武器池平均垫刀。
 */
fun List<GachaRecord>.limitedWeaponAvgPity(): Double {
    val weaponPool = AppConstants.resolveBannerPool(AppConstants.WEAPON_EVENT_BANNER)
    val weaponRecords = filter { it.gachaType in weaponPool }.sortedChronologically()
    val pities = averagePityCounter(weaponPool, weaponRecords, StandardItemUID)
    val avg = pities.average()
    return if (avg.isNaN()) 0.0 else avg
}


