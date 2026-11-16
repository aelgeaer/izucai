package com.izucai.morphology

import com.izucai.morphology.data.DisplaySettings
import com.izucai.morphology.data.ParadigmParser
import com.izucai.morphology.data.StressStyle
import com.izucai.morphology.data.TextNormalizer
import com.izucai.morphology.data.VerbParadigm
import com.izucai.morphology.ui.commonStem
import com.izucai.morphology.ui.styledForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun `normalisasi Rusia - huruf kecil, tanpa tanda tekan, ё menjadi е`() {
        assertEquals("машинами", TextNormalizer.normalizeRussian("  Маши́нами "))
        assertEquals("заказал", TextNormalizer.normalizeRussian("заказа'л"))
        assertEquals("елка", TextNormalizer.normalizeRussian("ЁЛКА"))
        assertEquals("кто-то", TextNormalizer.normalizeRussian("Кто-то?"))
        assertEquals("что это", TextNormalizer.normalizeRussian("что   это!"))
        // й tidak boleh rusak saat tanda gabung dibuang
        assertEquals("мой", TextNormalizer.normalizeRussian("мой"))
        assertEquals("мой", TextNormalizer.normalizeRussian("мой"))
    }

    @Test
    fun `deteksi bahasa dari alfabet`() {
        assertTrue(TextNormalizer.isCyrillic("машина"))
        assertTrue(TextNormalizer.isCyrillic("ok есть"))
        assertFalse(TextNormalizer.isCyrillic("mobil"))
        assertEquals("makan siang", TextNormalizer.normalizeIndonesian("  Makan   Siang. "))
    }

    @Test
    fun `batas atas awalan untuk pencarian berindeks`() {
        assertEquals("маш".dropLast(1) + 'щ', TextNormalizer.prefixUpperBound("маш"))
        assertTrue("машина" >= "маш" && "машина" < TextNormalizer.prefixUpperBound("маш"))
        assertFalse("мащ" < TextNormalizer.prefixUpperBound("маш"))
    }

    @Test
    fun `tampilan tanda tekan mengikuti pengaturan`() {
        val form = "заказа́л"
        assertEquals(form, TextNormalizer.displayStress(form, StressStyle.ACUTE))
        assertEquals("заказа'л", TextNormalizer.displayStress(form, StressStyle.APOSTROPHE))
        assertEquals("заказал", TextNormalizer.displayStress(form, StressStyle.NONE))
        assertEquals("есмь", TextNormalizer.speakable("(е́смь¹)"))
        assertEquals("едя", TextNormalizer.speakable("*едя́"))
    }

    @Test
    fun `akar kata dan pewarnaan akhiran`() {
        val forms = listOf("маши́на", "маши́ны", "маши́н", "маши́нами")
        assertEquals("машин", commonStem(forms))
        assertEquals("", commonStem(listOf("ко́фе")))

        val on = DisplaySettings(highlightEndings = true)
        val styled = styledForm("маши́нами", on, "машин")
        assertEquals("маши́нами", styled.text)
        assertEquals("akhiran «ами» diwarnai", 1, styled.spanStyles.size)
        assertEquals("ами", styled.text.substring(styled.spanStyles[0].start, styled.spanStyles[0].end))
        assertTrue(styledForm("маши́нами", on.copy(highlightEndings = false), "машин").spanStyles.isEmpty())

        val apostrophe = styledForm("заказа́ла", on.copy(stressStyle = StressStyle.APOSTROPHE), "заказа")
        assertEquals("заказа'ла", apostrophe.text)
        assertEquals("ла", apostrophe.text.substring(apostrophe.spanStyles[0].start))

        val rare = styledForm("*едя́", on)
        assertEquals("*едя́", rare.text)
        assertEquals(1, rare.spanStyles.size)
    }

    @Test
    fun `pengurai paradigm_json`() {
        val json = """{"kind":"verb","aspect":"pf","infinitive":["заказа́ть"],
            "future":{"sg1":["закажу́"],"sg2":[],"sg3":[],"pl1":[],"pl2":[],"pl3":[]},
            "past":{"m":["заказа́л"],"f":[],"n":[],"pl":[]},"imperative":{"sg":[],"pl":[]},
            "participles":{"past_pass":["зака́занный"]},"kolom_baru":1}"""
        val verb = ParadigmParser.parse(json) as VerbParadigm
        assertEquals("pf", verb.aspect)
        assertEquals(listOf("закажу́"), verb.future!!.sg1)
        assertEquals(listOf("зака́занный"), verb.participles.pastPass)
        assertTrue(verb.partners.isEmpty())
    }
}
