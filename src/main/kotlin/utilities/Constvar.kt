package utilities

object Constvar {
    val standard_character_ID: Set<String> = setOf(
        "10000016", "10000003", "10000035", "10000042",
        "10000041", "10000079", "10000069","10000104",
        "10000109"
    )

    val standard_Weapon_ID = setOf(
        "11502", "12501", "14501", "13501", "14502",
        "11501", "15501", "12502", "13502", "12503"
    )

    val bannerCode = setOf(
        "301", "302", "500", "400", "200", "100"
    )

    val weapon_CN_EN_Name = setOf("weapon", "武器")
    val character_CN_EN_Name = setOf("character", "角色")
}