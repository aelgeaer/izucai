package com.izucai.morphology

import com.izucai.morphology.data.AdjectiveParadigm
import com.izucai.morphology.data.MatchType
import com.izucai.morphology.data.NounParadigm
import com.izucai.morphology.data.QueryLanguage
import com.izucai.morphology.data.SearchOutcome
import com.izucai.morphology.data.SearchResultItem
import com.izucai.morphology.data.SimpleParadigm
import com.izucai.morphology.data.VerbParadigm
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Skenario wajib mesin pencarian, dijalankan terhadap basis data asli yang dikemas di APK.
 * (а́ = huruf + U+0301, tanda tekan.)
 */
class SearchScenariosTest {
    private val repository = TestDatabase.repository

    private fun search(query: String): SearchOutcome = runBlocking { repository.search(query) }

    private fun SearchOutcome.item(lemma: String, pos: String): SearchResultItem =
        items.firstOrNull { it.lemma.lemmaClean == lemma && it.lemma.pos == pos }
            ?: throw AssertionError("«$query»: lema $lemma [$pos] tidak ada di hasil ${items.map { it.lemma.lemmaClean to it.lemma.pos }}")

    // ------------------------------------------------------------ машина

    @Test
    fun `машина - bentuk dasar menampilkan 6 kasus tunggal dan jamak dengan aksen`() {
        val outcome = search("машина")
        assertEquals(QueryLanguage.RUSSIAN, outcome.language)
        val item = outcome.item("машина", "NOUN")
        assertEquals("маши́на", item.lemma.lemmaStressed)
        assertEquals(MatchType.LEMMA, item.matchType)
        assertTrue("bentuk dasar tidak menyorot sel mana pun", item.highlightKeys.isEmpty())
        assertEquals("жен.", item.lemma.gender)
        assertTrue(item.lemma.translationsId.contains("mobil"))

        val noun = item.paradigm as NounParadigm
        assertEquals(listOf("nom", "gen", "dat", "acc", "ins", "prep"), noun.rows.map { it.case })
        val table = noun.rows.associateBy { it.case }
        assertEquals(listOf("маши́на"), table.getValue("nom").sg)
        assertEquals(listOf("маши́ны"), table.getValue("nom").pl)
        assertEquals(listOf("маши́ны"), table.getValue("gen").sg)
        assertEquals(listOf("маши́н"), table.getValue("gen").pl)
        assertEquals(listOf("маши́не"), table.getValue("dat").sg)
        assertEquals(listOf("маши́нам"), table.getValue("dat").pl)
        assertEquals(listOf("маши́ну"), table.getValue("acc").sg)
        assertEquals(listOf("маши́ны"), table.getValue("acc").pl)
        assertEquals(listOf("маши́ной", "маши́ною"), table.getValue("ins").sg)
        assertEquals(listOf("маши́нами"), table.getValue("ins").pl)
        assertEquals(listOf("маши́не"), table.getValue("prep").sg)
        assertEquals(listOf("маши́нах"), table.getValue("prep").pl)
    }

    @Test
    fun `машинами - bentuk jamak instrumental tetap mengenali kata dasar машина`() {
        val item = search("машинами").item("машина", "NOUN")
        assertEquals(MatchType.INFLECTED, item.matchType)
        assertEquals(setOf("ins.pl"), item.highlightKeys)
        assertEquals("маши́нами", item.matchedForms.single().formStressed)
        assertEquals("Твор., Мн. ч.", item.matchedForms.single().gramInfo)
        assertEquals(6, (item.paradigm as NounParadigm).rows.size)
    }

    @Test
    fun `машиною dan МАШИНЫ - varian akhiran dan huruf besar`() {
        assertEquals(setOf("ins.sg"), search("машиною").item("машина", "NOUN").highlightKeys)
        assertEquals(setOf("nom.pl", "gen.sg", "acc.pl"), search("  МАШИНЫ ").item("машина", "NOUN").highlightKeys)
    }

    // -------------------------------------------------------------- есть

    @Test
    fun `есть - menampilkan kedua tabel homonim (makan dan быть)`() {
        val outcome = search("есть")
        val eat = outcome.item("есть", "VERB")
        val be = outcome.item("быть", "VERB")

        assertEquals(MatchType.LEMMA, eat.matchType)
        assertEquals("е́сть", eat.lemma.lemmaStressed)
        assertTrue(eat.lemma.translationsId.contains("makan"))
        assertEquals("Несовершенный вид (НСВ)", eat.lemma.aspect)
        val eatTable = eat.paradigm as VerbParadigm
        assertEquals("impf", eatTable.aspect)
        assertEquals(listOf("е́м"), eatTable.present!!.sg1)
        assertEquals(listOf("едя́т"), eatTable.present!!.pl3)
        assertNull("НСВ: kala depan dirakit dengan буду + infinitif", eatTable.future)
        assertEquals(listOf("е́л"), eatTable.past.m)
        assertEquals(listOf("е́шь"), eatTable.imperative.sg)
        assertEquals(listOf("е́в", "е́вши"), eatTable.participles.pastAdv)
        assertEquals("bentuk jarang ditandai bintang", listOf("*едя́"), eatTable.participles.presAdv)

        assertEquals(MatchType.INFLECTED, be.matchType)
        assertTrue(be.lemma.translationsId.split(", ").contains("ada"))
        val beTable = be.paradigm as VerbParadigm
        val full = assertNotNullAnd(beTable.fullTable)
        assertEquals(listOf("I лицо", "II лицо", "III лицо"), full.persons.map { it.label })
        assertEquals(listOf("бу́ду"), full.persons[0].futSg)
        assertEquals(listOf("бу́дь"), full.persons[1].impSg)
        assertTrue(full.participles.any { it.all == listOf("бу́дучи") })
        assertEquals(9, full.footnotes.size)
    }

    @Test
    fun `ели - bisa bentuk lampau есть maupun bentuk dari ель`() {
        val outcome = search("ели")
        assertEquals(setOf("past.pl"), outcome.item("есть", "VERB").highlightKeys)
        assertTrue("nom.pl" in outcome.item("ель", "NOUN").highlightKeys)
    }

    @Test
    fun `будучи dan бывший dikenali sebagai bentuk быть`() {
        assertEquals(setOf("part.pres_adv"), search("будучи").item("быть", "VERB").highlightKeys)
        assertEquals(setOf("part.past_act"), search("бывший").item("быть", "VERB").highlightKeys)
    }

    // ---------------------------------------------------------- заказать

    @Test
    fun `заказать dan заказала - keduanya menampilkan konjugasi perfektif заказать`() {
        val fromLemma = search("заказать").item("заказать", "VERB")
        val fromPast = search("заказала").item("заказать", "VERB")
        assertEquals(fromLemma.lemma.id, fromPast.lemma.id)
        assertEquals(MatchType.LEMMA, fromLemma.matchType)
        assertEquals(MatchType.INFLECTED, fromPast.matchType)
        assertEquals(setOf("past.f"), fromPast.highlightKeys)
        assertEquals("заказа́ла", fromPast.matchedForms.single().formStressed)
        assertEquals("Прош., жен., ед. ч.", fromPast.matchedForms.single().gramInfo)

        assertEquals("Совершенный вид (СВ)", fromLemma.lemma.aspect)
        val verb = fromPast.paradigm as VerbParadigm
        assertEquals("pf", verb.aspect)
        assertNull("СВ tidak punya kala kini", verb.present)
        val future = assertNotNullAnd(verb.future)
        assertEquals(
            listOf("закажу́", "зака́жешь", "зака́жет", "зака́жем", "зака́жете", "зака́жут"),
            listOf(future.sg1, future.sg2, future.sg3, future.pl1, future.pl2, future.pl3).map { it.single() },
        )
        assertEquals(listOf("заказа́л"), verb.past.m)
        assertEquals(listOf("заказа́ли"), verb.past.pl)
        assertEquals(listOf("закажи́"), verb.imperative.sg)
        assertEquals(listOf("закажи́те"), verb.imperative.pl)
        assertEquals(listOf("заказа́вший"), verb.participles.pastAct)
        assertEquals(listOf("заказа́в", "заказа́вши"), verb.participles.pastAdv)
        assertEquals(listOf("зака́занный"), verb.participles.pastPass)
        assertTrue(verb.participles.presAct.isEmpty())
        assertEquals(listOf("зака́зывать"), verb.partners)
    }

    @Test
    fun `покупать - dua homonim, masing-masing dengan terjemahannya sendiri`() {
        val items = search("покупать").items.filter { it.lemma.lemmaClean == "покупать" }
        assertEquals(2, items.size)
        val buy = items[0]
        val bathe = items[1]
        assertEquals("impf", (buy.paradigm as VerbParadigm).aspect)
        assertEquals("membeli", buy.lemma.translationsId)
        assertEquals(listOf("купи́ть"), (buy.paradigm as VerbParadigm).partners)
        assertEquals("pf", (bathe.paradigm as VerbParadigm).aspect)
        assertFalse(bathe.lemma.translationsId.contains("membeli"))
        assertTrue(bathe.lemma.translationsId.contains("memandikan"))
        assertEquals(listOf("купа́ть"), (bathe.paradigm as VerbParadigm).partners)

        // Pencarian Indonesia «membeli» tidak menampilkan arti "memandikan".
        val membeli = search("membeli").items
        assertTrue(membeli.any { it.lemma.id == buy.lemma.id })
        assertFalse(membeli.any { it.lemma.id == bathe.lemma.id })
    }

    // --------------------------------------------------------- Indonesia

    @Test
    fun `mobil, makan, memesan - kata Indonesia menampilkan lema Rusia beserta tabelnya`() {
        val car = search("mobil")
        assertEquals(QueryLanguage.INDONESIAN, car.language)
        assertEquals("машина", car.items.first().lemma.lemmaClean)
        assertEquals(MatchType.INDONESIAN, car.items.first().matchType)
        assertTrue(car.items.first().paradigm is NounParadigm)

        val eat = search("Makan")
        assertEquals("есть", eat.items.first().lemma.lemmaClean)
        assertTrue(eat.items.first().paradigm is VerbParadigm)

        val order = search("memesan")
        assertEquals("заказать", order.items.first().lemma.lemmaClean)
        assertEquals("pf", (order.items.first().paradigm as VerbParadigm).aspect)
        assertTrue(order.items.any { it.lemma.lemmaClean == "заказывать" })
    }

    @Test
    fun `pencarian Indonesia - awalan dan frasa`() {
        assertTrue(search("makan siang").items.any { it.lemma.lemmaClean == "обед" })
        assertTrue("awalan «memes» → memesan", search("memes").items.any { it.lemma.lemmaClean == "заказать" })
        assertTrue(search("qwertyzzz").items.isEmpty())
    }

    // ------------------------------------------------- normalisasi & lain

    @Test
    fun `normalisasi - tanda tekan, apostrof, dan ё sama dengan е`() {
        assertEquals("машина", search("маши́нами").items.first().lemma.lemmaClean)
        assertEquals("заказать", search("заказа'л").items.first().lemma.lemmaClean)
        assertNotNull(search("елка").items.firstOrNull { it.lemma.lemmaClean == "ёлка" })
        assertNotNull(search("шёл").items.firstOrNull { it.lemma.lemmaClean == "идти" })
        assertNotNull(search("шел").items.firstOrNull { it.lemma.lemmaClean == "идти" })
    }

    @Test
    fun `kata sifat, pronomina, numeralia`() {
        val adjective = search("новую").item("новый", "ADJ")
        assertEquals(setOf("acc_inan.f", "acc_anim.f"), adjective.highlightKeys)
        val table = adjective.paradigm as AdjectiveParadigm
        assertEquals(listOf("nom", "gen", "dat", "acc_inan", "acc_anim", "ins", "prep"), table.rows.map { it.case })
        assertEquals(listOf("но́вый"), table.rows[0].m)
        assertEquals(listOf("но́вого"), table.rows[4].m)
        assertEquals(listOf("нова́"), table.short!!.f)
        assertTrue(table.comparative.contains("нове́е"))

        val pronoun = search("меня").item("я", "PRON")
        assertEquals(setOf("gen", "acc"), pronoun.highlightKeys)
        assertTrue(pronoun.paradigm is SimpleParadigm)

        assertTrue(search("этого").item("этот", "PRON").paradigm is AdjectiveParadigm)
        assertTrue(search("пяти").items.any { it.lemma.lemmaClean == "пять" })
        assertTrue(search("детей").items.any { it.lemma.lemmaClean == "ребёнок" })
    }

    @Test
    fun `kata tidak dikenal mengembalikan saran terdekat`() {
        val outcome = search("машинка123")
        assertTrue(outcome.items.isEmpty())
        val unknown = search("заказзз")
        assertTrue(unknown.items.isEmpty())
        assertTrue("saran berawalan «заказ…»", unknown.alternatives.any { it.text.replace("́", "").startsWith("заказ") })
    }

    @Test
    fun `saran saat mengetik - Rusia dan Indonesia, minimal 2 huruf`() = runBlocking {
        assertTrue(repository.suggest("м").isEmpty())
        val russian = repository.suggest("маш")
        assertTrue(russian.any { it.text == "маши́на" })
        val inflected = repository.suggest("машинам")
        assertTrue("awalan bentuk infleksi → машина", inflected.any { it.hint.contains("маши́на") })
        val indonesian = repository.suggest("mob")
        assertTrue(indonesian.any { it.text == "mobil" && it.hint == "маши́на" })
        assertFalse(repository.suggest("ma").isEmpty())
    }

    @Test
    fun `statistik basis data tersedia`() = runBlocking {
        val stats = repository.stats()
        assertTrue(stats.getValue("lemma_count").toInt() >= 3000)
        assertTrue(stats.getValue("translated_count").toInt() >= 3000)
    }

    private fun <T : Any> assertNotNullAnd(value: T?): T {
        assertNotNull(value)
        return value!!
    }
}
