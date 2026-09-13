package analytics

import model.GachaRecord
import utilities.AppConstants
import utilities.AppConstants.GuaranteeType

object ExceptionalCharacters {
    // 特殊角色id
    private const val KEQING_ID = "10000042"
    private const val TIGHNARI_ID = "10000069"
    private const val DEHYA_ID = "10000079"
    private const val YUMEMIZUKI_ID = "10000109"

    // 曾经up的时间段
    private const val KEQING_START = "2021-02-17 18:00:00"
    private const val KEQING_END = "2021-03-02 15:59:59"

    private const val TIGHNARI_START = "2022-08-24 06:00:00"
    private const val TIGHNARI_END = "2022-09-09 17:59:59"

    private const val DEHYA_START = "2023-03-01 06:00:00"
    private const val DEHYA_END = "2023-03-21 17:59:59"

    private const val YUMEMIZUKI_START = "2025-05-14 06:00:00"
    private const val YUMEMIZUKI_END = "2025-06-03 17:59:59"

    /**
     * 检查歪的角色是否曾经up过
     */
    fun isCharacterWithinUpBannerPeriod (itemID: String, time: String): Boolean {
        return when (itemID) {
            KEQING_ID -> time in KEQING_START..KEQING_END
            TIGHNARI_ID -> time in TIGHNARI_START..TIGHNARI_END
            DEHYA_ID -> time in DEHYA_START..DEHYA_END
            YUMEMIZUKI_ID -> time in YUMEMIZUKI_START..YUMEMIZUKI_END
            else -> false
        }
    }

    fun getSetOfExceptionalCharacterID(): Set<String> {
        return setOf(KEQING_ID, TIGHNARI_ID, DEHYA_ID, YUMEMIZUKI_ID)
    }
}


fun losingStreakCounter(lastFiveWasLimited: Boolean, currentLosingStreak: Int): Int {
    return when (lastFiveWasLimited) {
        true -> 0
        false -> currentLosingStreak + 1
    }
}

// Handling the case in which parts of standard character were limited before
fun checkIfExceptionalCharacterWasLimited (record: GachaRecord, lastFiveWasLimited: Boolean): GuaranteeType {
    val time = record.time
    val itemId = record.itemID
    val pool = record.gachaType

    if ((pool == AppConstants.CHARACTER_EVENT_BANNER || pool == AppConstants.CHARACTER_EVENT_BANNER2)
        && ExceptionalCharacters.isCharacterWithinUpBannerPeriod(itemId, time)) {
        return if (lastFiveWasLimited) GuaranteeType.WON_FIFTY_FIFTY
        else GuaranteeType.GUARANTEED
    }

    return GuaranteeType.LOST_FIFTY_FIFTY
}