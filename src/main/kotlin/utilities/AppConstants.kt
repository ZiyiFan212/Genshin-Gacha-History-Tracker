package utilities

object AppConstants {
    // Update when new standard characters/weapons are added to the game.

    val StandardCharacterID: Set<String> = setOf(
        "10000016", "10000003", "10000035", "10000042",
        "10000041", "10000079", "10000069", "10000104",
        "10000109"
    )

    val StandardWeaponID = setOf(
        "11502", "12501", "14501", "13501", "14502",
        "11501", "15501", "12502", "13502", "12503"
    )

    val StandardItemUID = StandardCharacterID.union(StandardWeaponID)

    val BannerCodeList: List<String> = listOf("301", "400", "302", "500", "200", "100")

    /**
     * Character event banners (祈愿-1 / 祈愿-2) sharing pity and 50/50 guarantee state.
     * Using constant objects rather than hard coded string banner codes.
     */
    val CharacterEventBannerSet: Set<String> = setOf("301", "400")
    const val CHARACTER_EVENT_BANNER = "301"
    const val WEAPON_EVENT_BANNER = "302"
    const val CHARACTER_EVENT_BANNER2 = "400"
    const val CHRONICLE_EVENT_BANNER = "500"
    const val STANDARD_EVENT_BANNER = "200"
    const val NOVICE_EVENT_BANNER = "100"

    fun resolveBannerPool(banner: String): Set<String> =
        if (banner in CharacterEventBannerSet) CharacterEventBannerSet else setOf(banner)
}
