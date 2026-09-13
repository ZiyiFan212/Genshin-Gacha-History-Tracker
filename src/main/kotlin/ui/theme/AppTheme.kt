package ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// IntelliJ Light color palette
private val LightColors = lightColorScheme(
    primary = Color(0xFF0067B1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD1E4F7),
    onPrimaryContainer = Color(0xFF001D35),
    secondary = Color(0xFF6B6E70),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E6E6),
    onSecondaryContainer = Color(0xFF1F1F1F),
    tertiary = Color(0xFF59A869),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7F5E0),
    onTertiaryContainer = Color(0xFF00210F),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFF5F5F5),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFEDEDED),
    onSurfaceVariant = Color(0xFF4A4A4A),
    outline = Color(0xFFBFBFBF),
    outlineVariant = Color(0xFFD6D6D6),
    error = Color(0xFFC53B2C),
    onError = Color.White,
    errorContainer = Color(0xFFFDECEA),
    onErrorContainer = Color(0xFF410002),
)

// IntelliJ Darcula dark color palette
private val DarkColors = darkColorScheme(
    primary = Color(0xFF389FD6),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1F3A5F),
    onPrimaryContainer = Color(0xFFD1E4F7),
    secondary = Color(0xFF6B6E70),
    onSecondary = Color(0xFFE4E4E4),
    secondaryContainer = Color(0xFF3A3D41),
    onSecondaryContainer = Color(0xFFE4E4E4),
    tertiary = Color(0xFF59A869),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF1F4A2C),
    onTertiaryContainer = Color(0xFFB3F0C3),
    background = Color(0xFF2B2B2B),
    onBackground = Color(0xFFF0F0F0),
    surface = Color(0xFF3C3F41),
    onSurface = Color(0xFFF0F0F0),
    surfaceVariant = Color(0xFF4E5254),
    onSurfaceVariant = Color(0xFFA8A8A8),
    outline = Color(0xFF6B6E70),
    outlineVariant = Color(0xFF4E5254),
    error = Color(0xFFEF5B5B),
    onError = Color(0xFF2B0000),
    errorContainer = Color(0xFF5C1F1F),
    onErrorContainer = Color(0xFFFFDAD6),
)

// IntelliJ-style compact typography
private val IntelliJTypography = Typography(
    displayLarge = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    displayMedium = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    headlineLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineMedium = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    headlineSmall = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
    titleLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
    bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.3.sp),
    labelLarge = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

@Composable
fun GenshinTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = IntelliJTypography,
        content = content,
    )
}

@Composable
fun RankColor(rank: Int): Color = when (rank) {
    5 -> Color(0xFFFFD700)
    4 -> Color(0xFFBA68C8)
    else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
}