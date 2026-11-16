package com.izucai.morphology.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class QueryLanguage { RUSSIAN, INDONESIAN }

enum class MatchType {
    /** Masukan = bentuk kamus (машина, заказать). */
    LEMMA,

    /** Masukan = bentuk yang sudah berubah (машинами, заказала). */
    INFLECTED,

    /** Masukan = kata Indonesia. */
    INDONESIAN,
}

/** Bentuk yang cocok dengan masukan: "заказа́ла" + "Прош., жен., ед. ч.". */
data class MatchedForm(val formStressed: String, val gramInfo: String)

data class SearchResultItem(
    val lemma: LemmaEntity,
    val paradigm: Paradigm,
    val matchType: MatchType,
    val matchedForms: List<MatchedForm> = emptyList(),
    /** Kunci sel yang perlu disorot di tabel ("ins.pl", "past.f", ...). */
    val highlightKeys: Set<String> = emptySet(),
    /** Kata Indonesia yang cocok (untuk pencarian Indonesia). */
    val matchedIndonesian: String? = null,
)

data class SearchOutcome(
    val query: String,
    val language: QueryLanguage,
    val items: List<SearchResultItem>,
    /** Saran bila tidak ada hasil ("Mungkin maksud Anda ..."). */
    val alternatives: List<Suggestion> = emptyList(),
)

/**
 * Mesin pencarian pintar: menerima bentuk kata apa pun (Rusia) atau kata Indonesia, lalu
 * mengembalikan lema beserta tabel perubahannya. Bekerja 100% offline di atas SQLite read-only.
 */
class MorphologyRepository(
    private val dao: MorphologyDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun search(rawQuery: String): SearchOutcome = withContext(io) {
        if (TextNormalizer.isCyrillic(rawQuery)) searchRussian(rawQuery) else searchIndonesian(rawQuery)
    }

    suspend fun getLemma(id: Long): SearchResultItem? = withContext(io) {
        dao.getLemmas(listOf(id)).firstOrNull()?.let { SearchResultItem(it, ParadigmParser.parse(it.paradigmJson), MatchType.LEMMA) }
    }

    suspend fun suggest(rawQuery: String, limit: Int = 8): List<Suggestion> = withContext(io) {
        if (TextNormalizer.isCyrillic(rawQuery)) {
            val prefix = TextNormalizer.normalizeRussian(rawQuery)
            if (prefix.length < MIN_SUGGEST_LENGTH) return@withContext emptyList()
            val lemmas = dao.suggestLemmas(prefix, limit)
            if (lemmas.size >= limit || prefix.length < MIN_FORM_SUGGEST_LENGTH) return@withContext lemmas
            val known = lemmas.mapTo(HashSet()) { it.lemmaId }
            val forms = dao.suggestForms(prefix, limit * 3).filter { it.lemmaId !in known }.distinctBy { it.lemmaId }
            (lemmas + forms).take(limit)
        } else {
            val prefix = TextNormalizer.normalizeIndonesian(rawQuery)
            if (prefix.length < MIN_SUGGEST_LENGTH) return@withContext emptyList()
            dao.suggestIndonesian(prefix, limit * 3).distinctBy { it.text }.take(limit)
        }
    }

    suspend fun stats(): Map<String, String> = withContext(io) { dao.meta() }

    // ------------------------------------------------------------------ Rusia

    private fun searchRussian(rawQuery: String): SearchOutcome {
        val query = TextNormalizer.normalizeRussian(rawQuery)
        var forms = dao.findForms(query)
        if (forms.isEmpty() && ' ' in query) {
            // Beberapa kata sekaligus: cari masing-masing kata.
            forms = query.split(' ').distinct().take(MAX_WORDS).flatMap { dao.findForms(it) }
        }
        if (forms.isEmpty()) {
            return SearchOutcome(query, QueryLanguage.RUSSIAN, emptyList(), nearestRussian(query))
        }

        val byLemma = forms.groupBy { it.lemmaId }
        val lemmas = dao.getLemmas(byLemma.keys)
        val items = lemmas.map { lemma ->
            val matched = byLemma.getValue(lemma.id)
            val isDictionaryForm = lemma.lemmaNorm == query
            SearchResultItem(
                lemma = lemma,
                paradigm = ParadigmParser.parse(lemma.paradigmJson),
                matchType = if (isDictionaryForm) MatchType.LEMMA else MatchType.INFLECTED,
                matchedForms = if (isDictionaryForm) emptyList() else matched.map { MatchedForm(it.formStressed, it.gramInfo) },
                highlightKeys = if (isDictionaryForm) emptySet() else matched.flatMapTo(LinkedHashSet()) { it.cellKeyList },
            )
        }.sortedWith(compareBy<SearchResultItem> { it.matchType != MatchType.LEMMA }.thenBy { it.lemma.freqRank })
        return SearchOutcome(query, QueryLanguage.RUSSIAN, items.take(MAX_RESULTS))
    }

    /** Tidak ada yang cocok persis: tawarkan kata dengan awalan terpanjang yang sama. */
    private fun nearestRussian(query: String): List<Suggestion> {
        var prefix = query.substringBefore(' ')
        while (prefix.length >= MIN_SUGGEST_LENGTH) {
            val found = dao.suggestLemmas(prefix, 6)
            if (found.isNotEmpty()) return found
            prefix = prefix.dropLast(1)
        }
        return emptyList()
    }

    // -------------------------------------------------------------- Indonesia

    private fun searchIndonesian(rawQuery: String): SearchOutcome {
        val query = TextNormalizer.normalizeIndonesian(rawQuery)
        if (query.isEmpty()) return SearchOutcome(query, QueryLanguage.INDONESIAN, emptyList())

        // 1) kata/frasa persis  2) awalan ("pesan" → "pesanan")  3) potongan di kolom terjemahan
        var matches = dao.findIndonesian(query, MAX_RESULTS)
        if (matches.isEmpty()) matches = dao.findIndonesianPrefix(query, MAX_RESULTS)
        val matchedWord = matches.associate { it.lemmaId to it.indoWord }
        val ids: List<Long> = if (matches.isNotEmpty()) {
            matches.map { it.lemmaId }.distinct()
        } else {
            dao.findTranslationContaining(query, MAX_RESULTS)
        }
        if (ids.isEmpty()) {
            return SearchOutcome(query, QueryLanguage.INDONESIAN, emptyList(), dao.suggestIndonesian(query.take(3), 6))
        }
        val lemmas = dao.getLemmas(ids).associateBy { it.id }
        val items = ids.mapNotNull { id ->
            lemmas[id]?.let {
                SearchResultItem(
                    lemma = it,
                    paradigm = ParadigmParser.parse(it.paradigmJson),
                    matchType = MatchType.INDONESIAN,
                    matchedIndonesian = matchedWord[id] ?: query,
                )
            }
        }
        return SearchOutcome(query, QueryLanguage.INDONESIAN, items)
    }

    companion object {
        const val MIN_SUGGEST_LENGTH = 2
        const val MIN_FORM_SUGGEST_LENGTH = 4
        const val MAX_RESULTS = 12
        const val MAX_WORDS = 4
    }
}
