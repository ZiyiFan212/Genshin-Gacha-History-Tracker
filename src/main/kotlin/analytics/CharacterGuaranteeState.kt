package analytics

import model.GuaranteeType

/** Shared caller state for the merged 301/400 pool; update only after classification. */
internal class CharacterGuaranteeState {
    var hadFiveOnBanner = false
        private set
    var lastFiveWasLimited = false
        private set
    var losingStreak = 0
        private set

    fun update(type: GuaranteeType, time: String) {
        when (type) {
            GuaranteeType.LOST_FIFTY_FIFTY -> {
                hadFiveOnBanner = true
                lastFiveWasLimited = false
                // Pre-5.0 losses still establish guarantee, but never count for CR.
                losingStreak = if (TimeCheckForCR.afterVer5(time)) losingStreak + 1 else 0
            }
            GuaranteeType.GUARANTEED -> {
                hadFiveOnBanner = true
                lastFiveWasLimited = true
            }
            GuaranteeType.WON_FIFTY_FIFTY, GuaranteeType.CAPTURE_RADIANCE -> {
                hadFiveOnBanner = true
                lastFiveWasLimited = true
                losingStreak = 0
            }
            GuaranteeType.NONE, GuaranteeType.STANDARD -> Unit
        }
    }
}
