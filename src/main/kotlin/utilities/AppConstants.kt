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
    enum class Result {
        UP, LOSS, IGNORE,GUARANTEED_UP    }

    fun resolveBannerPool(banner: String): Set<String> =
        if (banner in CharacterEventBannerSet) CharacterEventBannerSet else setOf(banner)
}
