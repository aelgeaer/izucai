package com.izucai.morphology

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Uji instrumentasi alur pencarian di perangkat/emulator: basis data disalin dari assets,
 * dibuka dengan SQLite Android, dan tabel dirender oleh Compose.
 */
@RunWith(AndroidJUnit4::class)
class SearchFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun search(query: String) {
        rule.onNodeWithTag("search-field").performTextReplacement(query)
        rule.onNodeWithTag("search-button").performClick()
        rule.waitUntil(TIMEOUT_MS) {
            rule.onAllNodesWithTag("results").fetchSemanticsNodes().isNotEmpty() ||
                rule.onAllNodesWithTag("no-results").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun assertCard(lemma: String, pos: String) {
        val tag = "card:$lemma:$pos"
        rule.onNodeWithTag("results").performScrollToNode(hasTestTag(tag))
        rule.onNodeWithTag(tag).assertIsDisplayed()
    }

    /** Teks berada di dalam kontainer ber-tag (kontainer sendiri tidak menggabungkan teks anaknya). */
    private fun assertTextInside(tag: String, text: String) {
        rule.onNode(hasText(text, substring = true) and hasAnyAncestor(hasTestTag(tag))).assertIsDisplayed()
    }

    private fun assertFormShown(form: String) {
        rule.onNodeWithTag("results").performScrollToNode(hasText(form, substring = true))
        rule.onAllNodesWithText(form, substring = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun mashina_baseFormShowsSixCasesWithStress() {
        search("машина")
        assertCard("машина", "NOUN")
        rule.onNodeWithTag("lemma-title").assertTextContains("маши́на")
        rule.onNodeWithTag("translation-id").assertTextContains("mobil", substring = true)
        for (form in listOf("маши́ну", "маши́ною", "маши́нами", "маши́нах")) assertFormShown(form)
        check(rule.onAllNodesWithTag("inflected-badge").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun mashinami_inflectedFormResolvesToLemmaAndHighlightsCell() {
        search("машинами")
        assertCard("машина", "NOUN")
        assertTextInside("inflected-badge", "Твор., Мн. ч.")
        rule.onNodeWithTag("results").performScrollToNode(hasTestTag("highlight:ins.pl"))
        rule.onNodeWithTag("highlight:ins.pl", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun yest_showsBothHomonymTables() {
        search("есть")
        rule.onNodeWithTag("results").performScrollToNode(hasTestTag("result-count"))
        assertCard("есть", "VERB")
        assertFormShown("едя́т")
        assertCard("быть", "VERB")
        rule.onNodeWithTag("results").performScrollToNode(hasTestTag("table:full-tenses"))
        rule.onNodeWithTag("table:full-tenses").assertIsDisplayed()
    }

    @Test
    fun zakazat_andPastFeminine_showPerfectiveConjugation() {
        for (query in listOf("заказать", "заказала")) {
            search(query)
            assertCard("заказать", "VERB")
            assertTextInside("aspect", "Совершенный вид (СВ)")
            assertFormShown("закажу́")
            assertFormShown("зака́занный")
        }
        rule.onNodeWithTag("results").performScrollToNode(hasTestTag("highlight:past.f"))
        // «заказа́ла» muncul di baris Я, Ты, dan Она — ketiganya disorot.
        rule.onAllNodesWithTag("highlight:past.f", useUnmergedTree = true).onFirst().assertIsDisplayed()
    }

    @Test
    fun indonesianWords_showRussianLemmas() {
        for ((query, lemma, pos) in listOf(
            Triple("mobil", "машина", "NOUN"),
            Triple("makan", "есть", "VERB"),
            Triple("memesan", "заказать", "VERB"),
        )) {
            search(query)
            assertCard(lemma, pos)
        }
    }

    @Test
    fun tabs_helpAndAboutAreReachable() {
        rule.onNodeWithTag("tab:1").performClick()
        rule.onNodeWithTag("help-screen").assertIsDisplayed()
        rule.onNodeWithTag("tab:2").performClick()
        rule.onNodeWithTag("about-screen").assertIsDisplayed()
        rule.waitUntil(TIMEOUT_MS) { rule.onAllNodesWithText("Jumlah kata dasar (lema)").fetchSemanticsNodes().isNotEmpty() }
    }

    private companion object {
        /** Peluncuran pertama menyalin basis data ±60 MB dari assets. */
        const val TIMEOUT_MS = 60_000L
    }
}
