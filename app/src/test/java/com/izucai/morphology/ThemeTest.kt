package com.izucai.morphology

import androidx.compose.ui.graphics.Color
import com.izucai.morphology.data.DisplaySettings
import com.izucai.morphology.data.ThemeMode
import com.izucai.morphology.ui.DarkPalette
import com.izucai.morphology.ui.LightPalette
import com.izucai.morphology.ui.Palette
import com.izucai.morphology.ui.styledForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeTest {
    @Test
    fun `mode SYSTEM mengikuti sistem, TERANG dan GELAP memaksa`() {
        assertTrue(ThemeMode.SYSTEM.isDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.isDark(systemDark = false))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.isDark(systemDark = false))
        assertTrue(ThemeMode.DARK.isDark(systemDark = true))
        assertTrue(ThemeMode.DARK.isDark(systemDark = false))
    }

    @Test
    fun `nama tema tersimpan - tidak dikenal atau kosong jatuh ke SYSTEM`() {
        assertEquals(ThemeMode.DARK, ThemeMode.parse("DARK"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.parse("LIGHT"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse("SYSTEM"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.parse("dark"))
        assertEquals(ThemeMode.SYSTEM, DisplaySettings().themeMode)
        for (mode in ThemeMode.entries) assertEquals(mode, ThemeMode.parse(mode.name))
    }

    @Test
    fun `warna akhiran dan bentuk jarang mengikuti palet yang diberikan`() {
        val dark = styledForm("маши́нами", DisplaySettings(), "машин", DarkPalette.rare, DarkPalette.ending)
        assertEquals(DarkPalette.ending, dark.spanStyles.single().item.color)
        val rare = styledForm("*едя́", DisplaySettings(), "", DarkPalette.rare, DarkPalette.ending)
        assertEquals(DarkPalette.rare, rare.spanStyles.single().item.color)
        // Tanpa argumen warna: palet terang (dipakai tes lain).
        val default = styledForm("маши́нами", DisplaySettings(), "машин")
        assertEquals(LightPalette.ending, default.spanStyles.single().item.color)
    }

    // -------------------------------------------------------------- kontras (WCAG)

    private fun channel(v: Float): Double = if (v <= 0.03928f) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)

    private fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = luminance(a).let { la -> luminance(b).let { lb -> maxOf(la, lb) to minOf(la, lb) } }
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun Palette.checks(): List<Triple<String, Double, Double>> = listOf(
        // (pasangan, kontras aktual, minimum)
        Triple("teks utama / latar", contrast(textDark, background), 7.0),
        Triple("teks utama / permukaan", contrast(textDark, surface), 7.0),
        Triple("teks abu / latar", contrast(textGray, background), 4.5),
        Triple("teks abu / permukaan", contrast(textGray, surface), 4.5),
        Triple("teks samar / latar", contrast(textMuted, background), 3.0),
        Triple("teal / latar (tautan, indikator)", contrast(teal, background), 4.5),
        Triple("teal / permukaan", contrast(teal, surface), 4.5),
        Triple("teks utama / chip teal", contrast(textDark, tealSoft), 7.0),
        Triple("teks utama / tombol", contrast(textDark, buttonGray), 7.0),
        Triple("teks utama / label baris tabel", contrast(textDark, verbLabel), 7.0),
        Triple("teks utama / header hijau", contrast(textDark, pastelGreen), 7.0),
        Triple("teks utama / baris selang-seling", contrast(textDark, rowAlt), 7.0),
        Triple("teks utama / sorotan", contrast(textDark, highlight), 7.0),
        Triple("akhiran / permukaan", contrast(ending, surface), 4.5),
        Triple("akhiran / sorotan", contrast(ending, highlight), 4.5),
        Triple("akhiran / label baris tabel", contrast(ending, verbLabel), 4.5),
        Triple("bentuk jarang / permukaan", contrast(rare, surface), 4.5),
        Triple("teks lencana / teal", contrast(onAccent, teal), 4.5),
        Triple("teks lencana / samar", contrast(onAccent, textMuted), 3.0),
        Triple("teks snackbar / latar snackbar", contrast(snackbarText, snackbarBackground), 7.0),
        Triple("garis tabel / permukaan", contrast(gridBorder, surface), 3.0),
        Triple("garis bawah sel / latar", contrast(underline, background), 3.0),
    )

    private fun assertContrast(name: String, palette: Palette) {
        val failures = palette.checks().filter { (_, actual, min) -> actual < min }
            .map { (what, actual, min) -> "$what = ${"%.2f".format(actual)} (minimum $min)" }
        assertTrue("Palet $name: kontras kurang -> $failures", failures.isEmpty())
    }

    @Test
    fun `kontras palet terang memenuhi batas keterbacaan`() = assertContrast("terang", LightPalette)

    @Test
    fun `kontras palet gelap memenuhi batas keterbacaan`() = assertContrast("gelap", DarkPalette)

    @Test
    fun `palet gelap benar-benar gelap dan berbeda dari palet terang`() {
        assertTrue(luminance(DarkPalette.background) < 0.05)
        assertTrue(luminance(LightPalette.background) > 0.8)
        assertTrue("permukaan gelap lebih terang dari latar gelap", luminance(DarkPalette.surface) > luminance(DarkPalette.background))
        assertNotEquals(LightPalette, DarkPalette)
        // Pita status bar tetap gelap di kedua tema (rancangan referensi).
        assertTrue(luminance(LightPalette.systemBar) < 0.05)
        assertTrue(luminance(DarkPalette.systemBar) < 0.05)
    }
}
