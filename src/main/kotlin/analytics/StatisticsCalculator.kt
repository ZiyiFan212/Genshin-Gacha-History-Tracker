package analytics

import assets.UpTimeLoader
import model.GachaRecord
import model.UserStatistics
import model.sortedChronologically
import utilities.AppConstants
import utilities.AppConstants.StandardItemUID
import utilities.AppConstants.Result
import utilities.AppLogger
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 计算本用户的statistic，例如总五星，花销等，详见 [UserStatistics].
 *
 * 无参。
 * @return 数据类。
 */
fun List<GachaRecord>.calculateStat(): UserStatistics {
    // 花费计数。限定不只算
    val totalWishes: Int = this.size
    val totalWishesLim: Int =
        this.count {
            it.gachaType != "200" && it.gachaType != "500" && it.gachaType != "100"
        }
    // val totalWishChronicle: Int= this.count { it.gachatype = "500" }
    val totalPrimo: Int = totalWishes * 160

    // 物品计数
    val totalFiveStar: Int = this.count { it.rankType == 5 }
    val totalFourStar: Int = this.count { it.rankType == 4 }

    val first = this.filter { it.rankType == 5 && it.itemType == "character" }
    val first2 = this.filter { it.rankType == 5 && it.itemType == "weapon" }
    val totalFiveStarCharacter: Int = first.size
    val totalFiveStarWeapon: Int = first2.size

    val totalFiveStarCharacterLim: Int = first.count { !StandardItemUID.contains(it.itemID) }
    val totalFiveStarWeaponLim: Int = first2.count { !StandardItemUID.contains(it.itemID) }

    // 胜率
    val winrate = calculateWinRate(AppConstants.CharacterEventBannerSet)

    // 平均抽数。一般建议调真实抽数
    val avgPity = calculateAvgPity(AppConstants.BannerCodeList.toSet(), this)
    val avgPityLim = calculateUpPity(this)

    return UserStatistics(
        totalWishes, totalWishesLim, totalPrimo, totalFiveStar, totalFourStar,
        totalFiveStarCharacter, totalFiveStarWeapon, totalFiveStarCharacterLim, totalFiveStarWeaponLim,
        winrate, avgPity, avgPityLim
    )
}

/**
 * 计算 50/50 胜率（仅适用于角色卡池！！！）。
 *
 * 遵循 [analyzeStreaks] 的状态机逻辑：
 * - UP: 直接抽到限定
 * - LOSS: 歪
 * - GUARANTEED_UP: 歪了之后的保底限定 → 不计入
 *
 * @param bannerPool 卡池范围，如 [AppConstants.CharacterEventBannerSet]
 * @return 胜率百分比，无数据返回0
 */
fun List<GachaRecord>.calculateWinRate(bannerPool: Set<String>): Double {
    var wins = 0
    var losses = 0
    var prevState: Result? = null

    for (record in filter { it.gachaType in bannerPool }.sortedChronologically()) {
        if (record.rankType != 5) continue

        val isUp = !StandardItemUID.contains(record.itemID)
        val state = when {
            (isUp && prevState == Result.LOSS) -> Result.GUARANTEED_UP// 前一个歪了，本次就是guarantee
            isUp -> Result.UP
            else -> Result.LOSS
        }

        when (state) {
            Result.UP -> wins++
            Result.LOSS -> losses++
            Result.GUARANTEED_UP -> { /* 不计算 */ }
            Result.IGNORE -> { /* 仍然不计算 */ }
        }

        prevState = state
    }

    val total = wins + losses
    return if (total == 0) 0.0 else (wins.toDouble() / total.toDouble()) * 100.0
}

/**
 * 计算首发：复刻比。通过读取位于 /resource 中的 upTime.json 的时间对应。
 *
 * @param bannerPool 卡池范围，不提供则默认欸角色和武器
 * @return 返回pair， 首发和复刻占比。若初始化失败则返回 -1.
 */
fun List<GachaRecord>.upRatioCalculator(bannerPool: Set<String> = setOf(AppConstants.CHARACTER_EVENT_BANNER,
    AppConstants.CHARACTER_EVENT_BANNER2, AppConstants.WEAPON_EVENT_BANNER)): Pair<Double, Double> {

    UpTimeLoader.load().onFailure {
        AppLogger.warn("Failed to load h up-time map for all items!")
        return Pair(Double.NaN, Double.NaN) // check by caller (forced)
    }

    var up = 0
    var rerun = 0

    for (record in filter { it.gachaType in bannerPool }.sortedChronologically()) {
        if (record.rankType != 5) continue

        // ISO时间转epoch 13位时间戳
        val strTime: String = record.time
        val epochTime: Long = convertToEpochMillis(strTime)

        if (UpTimeLoader.isUpAtTime(record.itemID, epochTime)) {
            up++
        } else {
            rerun++
        }
    }

    val total = up + rerun
    if (total == 0) return Pair(0.0, 0.0)// 无记录，返回0
    val upRatio = up.toDouble() / total * 100.0
    return Pair(upRatio, 100.0 - upRatio)
}

private fun convertToEpochMillis(dateTimeStr: String): Long {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime = LocalDateTime.parse(dateTimeStr, formatter)
        localDateTime.atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
    } catch (e: Exception) {
        0L
    }
}


private fun calculateAvgPity(
    code: Set<String> = setOf("301", "302", "400"),
    records: List<GachaRecord>
): Double {
    val allPities = mutableListOf<Int>()
    val processedPools = mutableSetOf<Set<String>>()

    for (banner in code) {
        val pool = AppConstants.resolveBannerPool(banner)
        if (!processedPools.add(pool)) continue
        val result = averagePityCounter(pool, records)
        allPities.addAll(result)
    }
    val globalAvg = allPities.average()
    return if (globalAvg.isNaN()) 0.0 else globalAvg
}

private fun calculateUpPity(records: List<GachaRecord>): Double {

    // 角色 (banner.301 banner.400)
    val characterPool = AppConstants.CharacterEventBannerSet
    val characterList = averagePityCounter(characterPool, records, StandardItemUID)

    // 武器
    val weaponPool = AppConstants.resolveBannerPool(AppConstants.WEAPON_EVENT_BANNER)
    val weaponList = averagePityCounter(weaponPool, records)

    val allPities = characterList + weaponList
    val globalAvg = allPities.average()

    return if (globalAvg.isNaN()) 0.0 else globalAvg
}

fun averagePityCounter(
    bannerCodePool: Set<String>,
    records: List<GachaRecord>,
    standardItemSet: Set<String>? = null
): MutableList<Int> {
    val list = mutableListOf<Int>()
    var counter = 0
    val poolRecords = records.filter { it.gachaType in bannerCodePool }.sortedChronologically()
    for (record in poolRecords) {
        counter++
        if (record.rankType == 5) {
            if (standardItemSet == null || !standardItemSet.contains(record.itemID)) {
                list.add(counter)
                counter = 0
            }
        }
    }
    return list
}




