package com.family.inventory.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 与网页端 style.css 的 :root 变量保持同一套配色
val BrandBlue = Color(0xFF2563EB)
val BrandBlueDark = Color(0xFF1D4ED8)
val DangerRed = Color(0xFFDC2626)
val PageBg = Color(0xFFF4F6F8)
val CardWhite = Color(0xFFFFFFFF)
val TextMain = Color(0xFF1F2933)
val TextMuted = Color(0xFF6B7280)
val BorderGray = Color(0xFFE5E7EB)
val BadgeBg = Color(0xFFEEF2FF)
val BadgeText = Color(0xFF3730A3)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    primaryContainer = BadgeBg,
    onPrimaryContainer = BadgeText,
    secondary = BrandBlueDark,
    onSecondary = Color.White,
    background = PageBg,
    onBackground = TextMain,
    surface = CardWhite,
    onSurface = TextMain,
    surfaceVariant = Color(0xFFF1F3F6),
    onSurfaceVariant = TextMuted,
    outline = BorderGray,
    error = DangerRed
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF93B4FF),
    onPrimary = Color(0xFF0B2A6F),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDCE5FF),
    secondary = Color(0xFFAFC6FF),
    background = Color(0xFF121417),
    onBackground = Color(0xFFE6E8EB),
    surface = Color(0xFF1A1D21),
    onSurface = Color(0xFFE6E8EB),
    surfaceVariant = Color(0xFF242830),
    onSurfaceVariant = Color(0xFFA9B0BB),
    outline = Color(0xFF3A4048),
    error = Color(0xFFFF8A80)
)

@Composable
fun FamilyInventoryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = Typography(),
        content = content
    )
}
