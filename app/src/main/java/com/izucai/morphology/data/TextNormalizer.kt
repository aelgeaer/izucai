package com.izucai.morphology.data

import java.text.Normalizer
import java.util.Locale

/**
 * Normalisasi masukan pencarian. Harus sinkron dengan `data_pipeline/textnorm.py`,
 * karena hasilnya dicocokkan dengan kolom `word_forms.form_clean`.
 */
object TextNormalizer {
    const val ACUTE = '́'

    private val STRESS_CHARS = setOf('̀', '́', '́', '\'', '’', '`', '´')
    private val PUNCTUATION = Regex("[.,!?;:\"()\\[\\]«»“”„…*¹²³⁴⁵⁶⁷⁸⁹⁰]")
    private val SPACES = Regex("\\s+")

    /** Buang tanda tekan (aksen gabung maupun apostrof), pertahankan ё. */
    fun stripStress(text: String): String {
        val nfc = Normalizer.normalize(text, Normalizer.Form.NFC)
        return buildString(nfc.length) { for (c in nfc) if (c !in STRESS_CHARS) append(c) }
    }

    /** Kunci pencarian Rusia: huruf kecil, tanpa tanda tekan/tanda baca, ё → е. */
    fun normalizeRussian(text: String): String =
        stripStress(text).lowercase(Locale.ROOT).replace('ё', 'е')
            .replace(PUNCTUATION, " ").replace(SPACES, " ").trim()

    /** Kunci pencarian Indonesia: huruf kecil, tanpa tanda baca. */
    fun normalizeIndonesian(text: String): String =
        text.lowercase(Locale.ROOT).replace(PUNCTUATION, " ").replace(SPACES, " ").trim()

    fun isCyrillic(text: String): Boolean = text.any { it in 'Ѐ'..'ӿ' }

    /** Batas atas (eksklusif) untuk pencarian awalan memakai indeks: "маш" → "маш" + 1. */
    fun prefixUpperBound(prefix: String): String =
        if (prefix.isEmpty()) "￿" else prefix.dropLast(1) + (prefix.last() + 1)

    /** Ubah tampilan tanda tekan sesuai pengaturan pengguna. */
    fun displayStress(form: String, style: StressStyle): String = when (style) {
        StressStyle.ACUTE -> form
        StressStyle.APOSTROPHE -> form.replace(ACUTE, '\'')
        StressStyle.NONE -> form.replace(ACUTE.toString(), "")
    }

    /** Teks untuk dibacakan mesin TTS: tanpa tanda tekan, bintang, kurung, dan catatan kaki. */
    fun speakable(form: String): String =
        form.replace(ACUTE.toString(), "").replace(PUNCTUATION, " ").replace(SPACES, " ").trim()
}

enum class StressStyle { ACUTE, APOSTROPHE, NONE }
