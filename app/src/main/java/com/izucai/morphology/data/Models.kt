package com.izucai.morphology.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Satu baris tabel `lemmas`. */
data class LemmaEntity(
    val id: Long,
    val lemmaClean: String,
    val lemmaNorm: String,
    val lemmaStressed: String,
    val pos: String,
    val aspect: String?,
    val gender: String?,
    val translationsId: String,
    val translationsEn: String,
    val paradigmJson: String,
    val freqRank: Int,
)

/** Satu baris tabel `word_forms` (indeks terbalik: bentuk kata → lema). */
data class WordFormEntity(
    val formClean: String,
    val formStressed: String,
    val lemmaId: Long,
    val gramInfo: String,
    val cellKeys: String,
) {
    val cellKeyList: List<String> get() = cellKeys.split(';').filter { it.isNotEmpty() }
}

/** Sebuah sel tabel = daftar varian bentuk (dengan tanda tekan U+0301; awalan "*" = bentuk jarang). */
typealias Cell = List<String>

sealed interface Paradigm

// ---------------------------------------------------------------- kata benda

@Serializable
data class NounRow(
    val case: String,
    val sg: Cell = emptyList(),
    val pl: Cell = emptyList(),
)

@Serializable
data class NounParadigm(
    val rows: List<NounRow>,
    val animacy: String? = null,
    val indeclinable: Boolean = false,
    /** "sg_only" | "pl_only" | null */
    val number: String? = null,
) : Paradigm

// ---------------------------------------------------------------- kata sifat

@Serializable
data class GenderForms(
    val m: Cell = emptyList(),
    val f: Cell = emptyList(),
    val n: Cell = emptyList(),
    val pl: Cell = emptyList(),
) {
    operator fun get(column: String): Cell = when (column) {
        "m" -> m; "f" -> f; "n" -> n; else -> pl
    }
}

@Serializable
data class AdjectiveRow(
    val case: String,
    val m: Cell = emptyList(),
    val f: Cell = emptyList(),
    val n: Cell = emptyList(),
    val pl: Cell = emptyList(),
) {
    operator fun get(column: String): Cell = when (column) {
        "m" -> m; "f" -> f; "n" -> n; else -> pl
    }
}

@Serializable
data class AdjectiveParadigm(
    val rows: List<AdjectiveRow>,
    val short: GenderForms? = null,
    val comparative: Cell = emptyList(),
    val superlative: Cell = emptyList(),
) : Paradigm

// ------------------------------------------- tabel satu kolom (я, кто, пять)

@Serializable
data class SimpleRow(val case: String, val forms: Cell = emptyList())

@Serializable
data class SimpleParadigm(val rows: List<SimpleRow>) : Paradigm

// ---------------------------------------------------------------- kata kerja

@Serializable
data class PersonForms(
    val sg1: Cell = emptyList(),
    val sg2: Cell = emptyList(),
    val sg3: Cell = emptyList(),
    val pl1: Cell = emptyList(),
    val pl2: Cell = emptyList(),
    val pl3: Cell = emptyList(),
) {
    operator fun get(person: String): Cell = when (person) {
        "sg1" -> sg1; "sg2" -> sg2; "sg3" -> sg3; "pl1" -> pl1; "pl2" -> pl2; else -> pl3
    }
}

@Serializable
data class NumberForms(val sg: Cell = emptyList(), val pl: Cell = emptyList())

@Serializable
data class Participles(
    @SerialName("pres_act") val presAct: Cell = emptyList(),
    @SerialName("past_act") val pastAct: Cell = emptyList(),
    @SerialName("pres_adv") val presAdv: Cell = emptyList(),
    @SerialName("past_adv") val pastAdv: Cell = emptyList(),
    @SerialName("pres_pass") val presPass: Cell = emptyList(),
    @SerialName("past_pass") val pastPass: Cell = emptyList(),
)

/** Baris tabel lengkap быть: I / II / III лицо. */
@Serializable
data class FullPersonRow(
    val label: String,
    @SerialName("past_sg") val pastSg: Cell = emptyList(),
    @SerialName("past_pl") val pastPl: Cell = emptyList(),
    @SerialName("pres_sg") val presSg: Cell = emptyList(),
    @SerialName("pres_pl") val presPl: Cell = emptyList(),
    @SerialName("fut_sg") val futSg: Cell = emptyList(),
    @SerialName("fut_pl") val futPl: Cell = emptyList(),
    @SerialName("imp_sg") val impSg: Cell = emptyList(),
    @SerialName("imp_pl") val impPl: Cell = emptyList(),
)

/** Baris partisip per gender; `all` terisi bila satu nilai berlaku untuk semua kolom. */
@Serializable
data class FullParticipleRow(
    val label: String,
    val m: Cell = emptyList(),
    val f: Cell = emptyList(),
    val n: Cell = emptyList(),
    val pl: Cell = emptyList(),
    val all: Cell = emptyList(),
)

@Serializable
data class FullVerbTable(
    val persons: List<FullPersonRow> = emptyList(),
    val participles: List<FullParticipleRow> = emptyList(),
    val footnotes: List<String> = emptyList(),
)

@Serializable
data class VerbParadigm(
    /** "impf" | "pf" | "both" */
    val aspect: String,
    val infinitive: Cell = emptyList(),
    val present: PersonForms? = null,
    val future: PersonForms? = null,
    val past: GenderForms = GenderForms(),
    val imperative: NumberForms = NumberForms(),
    val participles: Participles = Participles(),
    val partners: List<String> = emptyList(),
    @SerialName("full_table") val fullTable: FullVerbTable? = null,
) : Paradigm

// ------------------------------------------------------------------ pengurai

object ParadigmParser {
    private val json = Json { ignoreUnknownKeys = true }

    /** Urai `paradigm_json`; jenis tabel ditentukan oleh kolom "kind". */
    fun parse(paradigmJson: String): Paradigm {
        val element = json.parseToJsonElement(paradigmJson)
        return when (val kind = element.jsonObject["kind"]?.jsonPrimitive?.content) {
            "noun" -> json.decodeFromJsonElement<NounParadigm>(element)
            "adj" -> json.decodeFromJsonElement<AdjectiveParadigm>(element)
            "verb" -> json.decodeFromJsonElement<VerbParadigm>(element)
            "simple" -> json.decodeFromJsonElement<SimpleParadigm>(element)
            else -> throw IllegalArgumentException("Jenis paradigma tak dikenal: $kind")
        }
    }
}
