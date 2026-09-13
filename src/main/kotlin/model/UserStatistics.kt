package model

import kotlinx.serialization.Serializable

@Serializable
data class UserStatistics(
    val totalWishes: Int,
    val totalWishesLim: Int,
    val totalPrimo: Int,

    val totalFiveStars: Int,
    val totalFourStars: Int,

    val fiveStarCharacter: Int,
    val fiveStarWeapon: Int,
    val fiveStarCharacterLim: Int,
    val fiveStarWeaponLim: Int,

    val winRate: Double,
    val avgPity: Double,
    val avgPityLim: Double
)