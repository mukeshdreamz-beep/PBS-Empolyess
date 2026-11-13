package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
data class AttendanceSemanticColors(
    val brandBadge: Color,
    val kpiBlueContainer: Color,
    val kpiBlueBorder: Color,
    val kpiBlueValue: Color,
    val fullDayAccent: Color,
    val fullDayContainer: Color,
    val fullDayBorder: Color,
    val onFullDayContainer: Color,
    val shortDayAccent: Color,
    val shortDayContainer: Color,
    val shortDayBorder: Color,
    val onShortDayContainer: Color,
    val overtimeAccent: Color,
    val overtimeContainer: Color,
    val overtimeBorder: Color,
    val onOvertimeContainer: Color
)

private val LightSemanticColors = AttendanceSemanticColors(
    brandBadge = BrandBlue,
    kpiBlueContainer = KpiBlueLightBg,
    kpiBlueBorder = KpiBlueLightBorder,
    kpiBlueValue = KpiBlueLightText,
    fullDayAccent = FullDayEmerald,
    fullDayContainer = FullDayEmeraldContainer,
    fullDayBorder = Color(0xFF86EFAC),
    onFullDayContainer = OnFullDayEmeraldContainer,
    shortDayAccent = ShortDayRose,
    shortDayContainer = ShortDayRoseContainer,
    shortDayBorder = Color(0xFFFCA5A5),
    onShortDayContainer = OnShortDayRoseContainer,
    overtimeAccent = OvertimeAmber,
    overtimeContainer = OvertimeAmberContainer,
    overtimeBorder = Color(0xFFFCD34D),
    onOvertimeContainer = OnOvertimeAmberContainer
)

private val DarkSemanticColors = AttendanceSemanticColors(
    brandBadge = BrandBlue,
    kpiBlueContainer = KpiBlueDarkBg,
    kpiBlueBorder = KpiBlueDarkBorder,
    kpiBlueValue = KpiBlueDarkText,
    fullDayAccent = DarkFullDayAccent,
    fullDayContainer = DarkFullDayContainer,
    fullDayBorder = DarkFullDayBorder,
    onFullDayContainer = DarkOnFullDayContainer,
    shortDayAccent = DarkShortDayAccent,
    shortDayContainer = DarkShortDayContainer,
    shortDayBorder = DarkShortDayBorder,
    onShortDayContainer = DarkOnShortDayContainer,
    overtimeAccent = DarkOvertimeAccent,
    overtimeContainer = DarkOvertimeContainer,
    overtimeBorder = DarkOvertimeBorder,
    onOvertimeContainer = DarkOnOvertimeContainer
)

val LocalAttendanceColors = staticCompositionLocalOf { DarkSemanticColors }

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

private val LightColorScheme = lightColorScheme(
    primary = NavyPrimary,
    onPrimary = OnNavyPrimary,
    primaryContainer = NavyPrimaryContainer,
    onPrimaryContainer = OnNavyPrimaryContainer,
    secondary = SlateSecondary,
    onSecondary = OnSlateSecondary,
    secondaryContainer = SlateSecondaryContainer,
    onSecondaryContainer = OnSlateSecondaryContainer,
    tertiary = AmberTertiary,
    onTertiary = OnAmberTertiary,
    tertiaryContainer = AmberTertiaryContainer,
    onTertiaryContainer = OnAmberTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun PbsAttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalAttendanceColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content
        )
    }
}
