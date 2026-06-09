package utilities

import template.GachaRecord
import template.PlayerStats

val standardUidSet = Constvar.standard_character_ID.union(Constvar.standard_Weapon_ID)

// Compute statistics
fun List<GachaRecord>.calculateStat(): PlayerStats{
    val totalWishes: Int = this.size
    val totalWishesLim: Int =
        this.count {
            it.gachaType != "200" && it.gachaType != "400" && it.gachaType != "100"
        }
    val totalPrimo: Int = totalWishes * 160

    val totalFiveStar: Int =
        this.count {
            it.rankType == 5
        }
    val totalFourStar: Int =
        this.count {
            it.rankType == 4
        }

    val first = this.partition { it.rankType == 5 && Constvar.character_CN_EN_Name.contains(it.itemType) }.first
    val totalFiveStarCharacter: Int = first.size
    val first2 = this.partition { it.rankType == 5 && Constvar.weapon_CN_EN_Name.contains(it.itemType) }.first
    val totalFiveStarWeapon: Int = first2.size

    val totalFiveStarCharacterLim: Int =
        first.count {
            !standardUidSet.contains(it.itemID)
        }
    val totalFiveStarWeaponLim: Int =
        first2.count {
            !standardUidSet.contains(it.itemID)
        }


    var total = 0
    var win = 0
    for (record in first.union(first2)) {
        if (record.gachaType != "301" && record.gachaType != "500" ) continue
        println(record.name + record.itemID)
        total++
        if (!standardUidSet.contains(record.itemID)) win++
    }
    val winrate: Double = if (total == 0) 0.0 else (win.toDouble() / total.toDouble()) * 100.0


    val avgPity = calculateAvgPity(Constvar.bannerCode, this)
    val avgPityLim = calculateAvgPity(records = this)

    return PlayerStats(totalWishes, totalWishesLim, totalPrimo, totalFiveStar, totalFourStar,
        totalFiveStarCharacter, totalFiveStarWeapon, totalFiveStarCharacterLim, totalFiveStarWeaponLim,
        winrate, avgPity, avgPityLim)
}

// Function to calculate average pity to obtain a five-star item
private fun calculateAvgPity(code: Set<String> = setOf("301", "302", "500", "400"), records: List<GachaRecord>): Double {
    val allPities = mutableListOf<Int>()

    for (banner in code) {
        var pityCount = 0
        val bannerRecords = records.filter { it.gachaType == banner }.sortedBy { it.time }
        for (record in bannerRecords) {
            pityCount++
            if (record.rankType == 5) {
                allPities.add(pityCount)
                pityCount = 0
            }
        }
    }
    val globalAvg = allPities.average()
    return if (globalAvg.isNaN()) 0.0 else globalAvg
}

