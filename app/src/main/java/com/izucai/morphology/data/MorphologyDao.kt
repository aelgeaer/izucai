package com.izucai.morphology.data

/** Satu baris hasil kueri (indeks kolom mulai dari 0). */
interface SqlRow {
    fun string(index: Int): String?
    fun long(index: Int): Long
}

/**
 * Abstraksi tipis di atas koneksi SQLite read-only. Di perangkat diimplementasikan dengan
 * `android.database.sqlite` ([AndroidSqlRunner]); di unit test JVM dengan JDBC, sehingga SQL
 * yang sama persis diuji terhadap berkas .db yang sama.
 */
interface SqlRunner {
    fun <T> query(sql: String, args: List<String> = emptyList(), map: (SqlRow) -> T): List<T>
}

data class IndoMatch(val indoWord: String, val lemmaId: Long, val priority: Int)

data class Suggestion(
    /** Teks yang diketik pengguna bila saran ini dipilih. */
    val text: String,
    /** Keterangan di sebelah kanan (kata dasar / terjemahan). */
    val hint: String,
    val lemmaId: Long,
)

/** Seluruh kueri SQL aplikasi. Semua pemanggilan bersifat sinkron; pemindahan ke thread IO dilakukan di repository. */
class MorphologyDao(private val sql: SqlRunner) {

    fun findForms(formClean: String): List<WordFormEntity> = sql.query(
        "SELECT form_clean, form_stressed, lemma_id, gram_info, cell_keys FROM word_forms WHERE form_clean = ?",
        listOf(formClean),
    ) { WordFormEntity(it.string(0)!!, it.string(1)!!, it.long(2), it.string(3)!!, it.string(4)!!) }

    fun getLemmas(ids: Collection<Long>): List<LemmaEntity> {
        if (ids.isEmpty()) return emptyList()
        return sql.query("SELECT $LEMMA_COLUMNS FROM lemmas WHERE id IN (${ids.joinToString(",")})", map = ::readLemma)
    }

    fun findIndonesian(word: String, limit: Int): List<IndoMatch> = sql.query(
        "SELECT i.indo_word, i.lemma_id, i.priority FROM id_search_index i JOIN lemmas l ON l.id = i.lemma_id " +
            "WHERE i.indo_word = ? ORDER BY i.priority, l.freq_rank LIMIT $limit",
        listOf(word),
    ) { IndoMatch(it.string(0)!!, it.long(1), it.long(2).toInt()) }

    fun findIndonesianPrefix(prefix: String, limit: Int): List<IndoMatch> = sql.query(
        "SELECT i.indo_word, i.lemma_id, i.priority FROM id_search_index i JOIN lemmas l ON l.id = i.lemma_id " +
            "WHERE i.indo_word >= ? AND i.indo_word < ? ORDER BY i.priority, l.freq_rank LIMIT $limit",
        listOf(prefix, TextNormalizer.prefixUpperBound(prefix)),
    ) { IndoMatch(it.string(0)!!, it.long(1), it.long(2).toInt()) }

    /** Cadangan terakhir: cari potongan teks di dalam kolom terjemahan. */
    fun findTranslationContaining(text: String, limit: Int): List<Long> {
        val pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"
        return sql.query(
            "SELECT id FROM lemmas WHERE translations_id != '' AND translations_id LIKE ? ESCAPE '\\' " +
                "ORDER BY freq_rank LIMIT $limit",
            listOf(pattern),
        ) { it.long(0) }
    }

    fun suggestLemmas(prefixNorm: String, limit: Int): List<Suggestion> = sql.query(
        "SELECT id, lemma_stressed, translations_id, translations_en FROM lemmas " +
            "WHERE lemma_norm >= ? AND lemma_norm < ? ORDER BY freq_rank LIMIT $limit",
        listOf(prefixNorm, TextNormalizer.prefixUpperBound(prefixNorm)),
    ) { Suggestion(it.string(1)!!, it.string(2).orEmpty().ifEmpty { it.string(3).orEmpty() }, it.long(0)) }

    /** Saran dari bentuk infleksi: "машинам…" → машинами (маши́на). */
    fun suggestForms(prefixNorm: String, limit: Int): List<Suggestion> = sql.query(
        "SELECT w.form_stressed, l.lemma_stressed, l.id FROM word_forms w JOIN lemmas l ON l.id = w.lemma_id " +
            "WHERE w.form_clean >= ? AND w.form_clean < ? ORDER BY l.freq_rank, w.form_clean LIMIT $limit",
        listOf(prefixNorm, TextNormalizer.prefixUpperBound(prefixNorm)),
    ) { Suggestion(it.string(0)!!, "→ " + it.string(1)!!, it.long(2)) }

    fun suggestIndonesian(prefix: String, limit: Int): List<Suggestion> = sql.query(
        "SELECT i.indo_word, l.lemma_stressed, l.id FROM id_search_index i JOIN lemmas l ON l.id = i.lemma_id " +
            "WHERE i.indo_word >= ? AND i.indo_word < ? ORDER BY i.priority, l.freq_rank LIMIT $limit",
        listOf(prefix, TextNormalizer.prefixUpperBound(prefix)),
    ) { Suggestion(it.string(0)!!, it.string(1)!!, it.long(2)) }

    fun meta(): Map<String, String> =
        sql.query("SELECT key, value FROM meta") { it.string(0)!! to it.string(1).orEmpty() }.toMap()

    private fun readLemma(row: SqlRow) = LemmaEntity(
        id = row.long(0),
        lemmaClean = row.string(1)!!,
        lemmaNorm = row.string(2)!!,
        lemmaStressed = row.string(3)!!,
        pos = row.string(4)!!,
        aspect = row.string(5),
        gender = row.string(6),
        translationsId = row.string(7).orEmpty(),
        translationsEn = row.string(8).orEmpty(),
        paradigmJson = row.string(9)!!,
        freqRank = row.long(10).toInt(),
    )

    private companion object {
        const val LEMMA_COLUMNS =
            "id, lemma_clean, lemma_norm, lemma_stressed, pos, aspect, gender, translations_id, translations_en, paradigm_json, freq_rank"
    }
}
