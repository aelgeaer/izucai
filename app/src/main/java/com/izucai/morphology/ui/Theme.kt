package com.izucai.morphology.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palet warna mengikuti rancangan referensi. */
object AppColors {
    val Teal = Color(0xFF00796B)
    val TealSoft = Color(0xFFE0F2F1)
    val SystemBar = Color(0xFF1E1A17)
    val Background = Color(0xFFFAFAFA)
    val TextDark = Color(0xFF212121)
    val TextGray = Color(0xFF616161)
    val TextMuted = Color(0xFF8A8A8A)
    val Underline = Color(0xFF757575)
    val ButtonGray = Color(0xFFD6D7D7)
    val GridBorder = Color(0xFF333333)
    val VerbLabel = Color(0xFFEBF3F9)
    val PastelGreen = Color(0xFFE8F8E8)
    val RowAlt = Color(0xFFF0F0F0)
    val Rare = Color(0xFF8E1B3A)
    val Highlight = Color(0xFFFFF0B3)
    val Ending = Color(0xFFC2410C)
}

private val Colors = lightColorScheme(
    primary = AppColors.Teal,
    onPrimary = Color.White,
    secondary = AppColors.Teal,
    background = AppColors.Background,
    surface = Color.White,
    onBackground = AppColors.TextDark,
    onSurface = AppColors.TextDark,
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = AppColors.TextGray,
)

@Composable
fun IzucaiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
