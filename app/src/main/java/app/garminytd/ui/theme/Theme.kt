package app.garminytd.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Chart/semantic colours that Material's scheme has no slot for. */
@Immutable
data class RideColors(
    val thisYear: Color,
    val lastYear: Color,
    val projection: Color,
    val positive: Color,
    val negative: Color,
    val gridLine: Color,
    val heatEmpty: Color,
    val heatScale: List<Color>,
)

private val Orange = Color(0xFFFF7A1A)

private val DarkRide = RideColors(
    thisYear = Orange,
    lastYear = Color(0xFF7D8CA3),
    projection = Color(0xFFFFB27A),
    positive = Color(0xFF4ADE80),
    negative = Color(0xFFF87171),
    gridLine = Color(0x1FFFFFFF),
    heatEmpty = Color(0xFF1E242C),
    heatScale = listOf(Color(0xFF5A2E10), Color(0xFF8F4310), Color(0xFFC95A12), Color(0xFFFF7A1A)),
)

private val LightRide = RideColors(
    thisYear = Color(0xFFE5600A),
    lastYear = Color(0xFF5B6B7F),
    projection = Color(0xFFF2A066),
    positive = Color(0xFF15803D),
    negative = Color(0xFFDC2626),
    gridLine = Color(0x1A000000),
    heatEmpty = Color(0xFFE7EAEE),
    heatScale = listOf(Color(0xFFFFD9BC), Color(0xFFFFB27A), Color(0xFFFF8B3D), Color(0xFFE5600A)),
)

private val DarkScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Color(0xFF1A0E05),
    secondary = Color(0xFF7D8CA3),
    secondaryContainer = Color(0xFF4A2A12),
    onSecondaryContainer = Color(0xFFFFD2B0),
    background = Color(0xFF0F1216),
    onBackground = Color(0xFFE8ECF1),
    surface = Color(0xFF0F1216),
    onSurface = Color(0xFFE8ECF1),
    surfaceContainer = Color(0xFF171C22),
    surfaceContainerHigh = Color(0xFF1E242C),
    surfaceVariant = Color(0xFF1E242C),
    onSurfaceVariant = Color(0xFF9AA6B5),
    outline = Color(0xFF3A4350),
    outlineVariant = Color(0xFF2A313A),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFFE5600A),
    onPrimary = Color.White,
    secondary = Color(0xFF5B6B7F),
    secondaryContainer = Color(0xFFFFE0C7),
    onSecondaryContainer = Color(0xFF5A2600),
    background = Color(0xFFF6F7F9),
    onBackground = Color(0xFF11161C),
    surface = Color(0xFFF6F7F9),
    onSurface = Color(0xFF11161C),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFEEF0F3),
    surfaceVariant = Color(0xFFEEF0F3),
    onSurfaceVariant = Color(0xFF5B6675),
    outline = Color(0xFFC5CBD3),
    outlineVariant = Color(0xFFE1E5EA),
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
        displayMedium = displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = labelSmall.copy(letterSpacing = 0.8.sp),
    )
}

/** Tabular figures so numbers don't jiggle while scrubbing the chart. */
val NumberStyle = TextStyle(fontFamily = FontFamily.Default, fontFeatureSettings = "tnum")

val LocalRideColors = staticCompositionLocalOf { DarkRide }

/** For drawing outside Compose (the home-screen widget's chart bitmap). */
fun rideColors(dark: Boolean): RideColors = if (dark) DarkRide else LightRide

@Composable
fun GarminYtdTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalRideColors provides if (dark) DarkRide else LightRide) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

object RideTheme {
    val colors: RideColors @Composable get() = LocalRideColors.current
}
