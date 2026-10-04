package utilities.preference

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class ThemeModeManager {
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
    @SerialName("system") SYSTEM,
}