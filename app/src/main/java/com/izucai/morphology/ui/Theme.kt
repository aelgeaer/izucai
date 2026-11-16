package com.izucai.morphology.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Seluruh warna aplikasi untuk satu tema (terang atau gelap). */
data class Palette(
    val teal: Color,
    val tealSoft: Color,
    /** Pita di belakang status bar & bilah navigasi (selalu gelap, seperti rancangan referensi). */
    val systemBar: Color,
    val background: Color,
    /** Permukaan kartu, daftar saran, dan sel tabel biasa. */
    val surface: Color,
    /** Teks utama. */
    val textDark: Color,
    val textGray: Color,
    val textMuted: Color,
    val underline: Color,
    val buttonGray: Color,
    val gridBorder: Color,
    val verbLabel: Color,
    val pastelGreen: Color,
    val rowAlt: Color,
    /** Bentuk jarang/usang (*едя́). */
    val rare: Color,
    /** Sel/badge yang disorot saat pencarian dari bentuk yang sudah berubah. */
    val highlight: Color,
    /** Akhiran kata yang diwarnai. */
    val ending: Color,
    /** Garis pemisah tipis. */
    val divider: Color,
    /** Pita pemisah tebal antarkartu hasil. */
    val separator: Color,
    /** Teks/ikon di atas latar aksen (lencana ID/EN). */
    val onAccent: Color,
    val snackbarBackground: Color,
    val snackbarText: Color,
)

/** Palet terang — mengikuti rancangan referensi. */
val LightPalette = Palette(
    teal = Color(0xFF00796B),
    tealSoft = Color(0xFFE0F2F1),
    systemBar = Color(0xFF1E1A17),
    background = Color(0xFFFAFAFA),
    surface = Color(0xFFFFFFFF),
    textDark = Color(0xFF212121),
    textGray = Color(0xFF616161),
    textMuted = Color(0xFF8A8A8A),
    underline = Color(0xFF757575),
    buttonGray = Color(0xFFD6D7D7),
    gridBorder = Color(0xFF333333),
    verbLabel = Color(0xFFEBF3F9),
    pastelGreen = Color(0xFFE8F8E8),
    rowAlt = Color(0xFFF0F0F0),
    rare = Color(0xFF8E1B3A),
    highlight = Color(0xFFFFF0B3),
    ending = Color(0xFFC2410C),
    divider = Color(0xFFE9E9E9),
    separator = Color(0xFFEDEDED),
    onAccent = Color(0xFFFFFFFF),
    snackbarBackground = Color(0xFF323232),
    snackbarText = Color(0xFFFFFFFF),
)

/** Palet gelap — latar hampir hitam, aksen teal dan oranye diterangkan agar kontrasnya tetap cukup. */
val DarkPalette = Palette(
    teal = Color(0xFF4DB6AC),
    tealSoft = Color(0xFF1F3A37),
    systemBar = Color(0xFF0C0B0A),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    textDark = Color(0xFFEDEDED),
    textGray = Color(0xFFB4B4B4),
    textMuted = Color(0xFF8E8E8E),
    underline = Color(0xFF8A8A8A),
    buttonGray = Color(0xFF3A3F3F),
    gridBorder = Color(0xFF707070),
    verbLabel = Color(0xFF1F2B35),
    pastelGreen = Color(0xFF1E2F24),
    rowAlt = Color(0xFF282828),
    rare = Color(0xFFFF8FAB),
    highlight = Color(0xFF4D4010),
    ending = Color(0xFFFFA070),
    divider = Color(0xFF333333),
    separator = Color(0xFF0A0A0A),
    onAccent = Color(0xFF101010),
    snackbarBackground = Color(0xFFE4E4E4),
    snackbarText = Color(0xFF1A1A1A),
)

val LocalPalette = staticCompositionLocalOf { LightPalette }

/**
 * Akses warna tema yang sedang aktif: `AppColors.Teal`, `AppColors.Surface`, dst.
 * Hanya bisa dibaca di dalam fungsi @Composable (nilainya berubah mengikuti tema).
 */
object AppColors {
    val Teal: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.teal
    val TealSoft: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.tealSoft
    val SystemBar: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.systemBar
    val Background: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.background
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.surface
    val TextDark: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textDark
    val TextGray: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textGray
    val TextMuted: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.textMuted
    val Underline: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.underline
    val ButtonGray: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.buttonGray
    val GridBorder: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.gridBorder
    val VerbLabel: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.verbLabel
    val PastelGreen: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.pastelGreen
    val RowAlt: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.rowAlt
    val Rare: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.rare
    val Highlight: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.highlight
    val Ending: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.ending
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.divider
    val Separator: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.separator
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.onAccent
    val SnackbarBackground: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.snackbarBackground
    val SnackbarText: Color @Composable @ReadOnlyComposable get() = LocalPalette.current.snackbarText
}

private fun lightScheme(p: Palette) = lightColorScheme(
    primary = p.teal,
    onPrimary = Color.White,
    secondary = p.teal,
    background = p.background,
    surface = p.surface,
    onBackground = p.textDark,
    onSurface = p.textDark,
    surfaceVariant = Color(0xFFF1F1F1),
    onSurfaceVariant = p.textGray,
)

private fun darkScheme(p: Palette) = darkColorScheme(
    primary = p.teal,
    onPrimary = Color(0xFF00201C),
    secondary = p.teal,
    background = p.background,
    surface = p.surface,
    onBackground = p.textDark,
    onSurface = p.textDark,
    surfaceVariant = Color(0xFF2A2D2D),
    onSurfaceVariant = p.textGray,
    outline = Color(0xFF8A8A8A),
)

@Composable
fun IzucaiTheme(darkTheme: Boolean = false, content: @Composable () -> Unit) {
    val palette = if (darkTheme) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalPalette provides palette,
        // Teks tanpa warna eksplisit tidak boleh jatuh ke hitam default di atas latar gelap.
        LocalContentColor provides palette.textDark,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) darkScheme(palette) else lightScheme(palette), content = content)
    }
}
