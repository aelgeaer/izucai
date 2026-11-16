package com.izucai.morphology.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izucai.morphology.data.AdjectiveParadigm
import com.izucai.morphology.data.Cell
import com.izucai.morphology.data.DisplaySettings
import com.izucai.morphology.data.FullVerbTable
import com.izucai.morphology.data.NounParadigm
import com.izucai.morphology.data.Paradigm
import com.izucai.morphology.data.SimpleParadigm
import com.izucai.morphology.data.StressStyle
import com.izucai.morphology.data.TextNormalizer
import com.izucai.morphology.data.VerbParadigm

/** Konteks bersama seluruh sel tabel: pengaturan tampilan, sel yang disorot, dan aksi ketuk. */
class TableContext(
    val settings: DisplaySettings,
    val highlightKeys: Set<String>,
    /** (bentuk kata, keterangan gramatikal) → bacakan + tampilkan keterangan. */
    val onTap: (form: String, info: String) -> Unit,
)

val LocalTable = compositionLocalOf { TableContext(DisplaySettings(), emptySet()) { _, _ -> } }

private val FORM_SIZE = 17.sp
private val GRID_SIZE = 15.sp

/** Gaya teks di dalam tabel berbatas: tanpa jarak antarhuruf agar lebar kolom terukur tepat. */
private val GRID_STYLE = TextStyle(fontSize = GRID_SIZE, letterSpacing = 0.sp, lineHeight = 20.sp)

// --------------------------------------------------------------------- util

private fun bare(form: String): String =
    TextNormalizer.stripStress(form.removePrefix("*")).lowercase()

/** Awalan terpanjang yang sama pada semua bentuk = akar kata; sisanya dianggap akhiran. */
fun commonStem(forms: Iterable<String>): String {
    val words = forms.map(::bare).filter { it.isNotEmpty() && ' ' !in it && '(' !in it }.distinct()
    if (words.size < 2) return ""
    var prefix = words.first()
    for (w in words) {
        var i = 0
        while (i < prefix.length && i < w.length && prefix[i] == w[i]) i++
        prefix = prefix.substring(0, i)
        if (prefix.isEmpty()) break
    }
    return prefix
}

/** Bentuk kata siap tampil: tanda tekan sesuai pengaturan, bentuk jarang (*), pewarnaan akhiran. */
fun styledForm(form: String, settings: DisplaySettings, stem: String = ""): AnnotatedString = buildAnnotatedString {
    val rare = form.startsWith("*")
    val body = TextNormalizer.displayStress(form.removePrefix("*"), settings.stressStyle)
    if (rare) {
        withStyle(SpanStyle(color = AppColors.Rare, fontStyle = FontStyle.Italic)) { append("*"); append(body) }
        return@buildAnnotatedString
    }
    if (!settings.highlightEndings || stem.isEmpty() || !bare(form).startsWith(stem)) {
        append(body)
        return@buildAnnotatedString
    }
    var letters = 0
    var split = body.length
    for ((i, ch) in body.withIndex()) {
        if (ch == TextNormalizer.ACUTE || (ch == '\'' && settings.stressStyle == StressStyle.APOSTROPHE)) continue
        if (letters == stem.length) { split = i; break }
        letters++
    }
    append(body.substring(0, split))
    withStyle(SpanStyle(color = AppColors.Ending)) { append(body.substring(split)) }
}

/**
 * Mengukur lebar kolom dari teks sebenarnya (bentuk terpanjang + judul kolom), supaya tabel
 * serapat mungkin dan kata tidak terpotong di tengah.
 */
private class ColumnMeasure(
    private val measurer: TextMeasurer,
    private val density: Density,
    private val stressStyle: StressStyle,
) {
    private fun pixels(text: String, bold: Boolean): Int = text.split('\n').maxOf { line ->
        measurer.measure(
            text = TextNormalizer.displayStress(line, stressStyle),
            style = GRID_STYLE.copy(fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
            softWrap = false,
            maxLines = 1,
        ).size.width
    }

    fun width(forms: Iterable<String>, headers: Iterable<String> = emptyList(), min: Dp = 40.dp): Dp {
        val widest = maxOf(forms.maxOfOrNull { pixels(it, false) } ?: 0, headers.maxOfOrNull { pixels(it, true) } ?: 0)
        return maxOf(min, with(density) { widest.toDp() } + CELL_PADDING * 2 + 3.dp)
    }
}

@Composable
private fun rememberColumnMeasure(): ColumnMeasure {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val stressStyle = LocalTable.current.settings.stressStyle
    return remember(measurer, density, stressStyle) { ColumnMeasure(measurer, density, stressStyle) }
}

private val CELL_PADDING = 4.dp

private fun List<Dp>.total(): Dp = fold(0.dp) { acc, d -> acc + d }

// ------------------------------------------------------------- sel & bentuk

/** Daftar varian dalam satu sel; setiap varian bisa diketuk untuk dibacakan. */
@Composable
private fun Forms(
    forms: Cell,
    info: String,
    stem: String = "",
    fontSize: TextUnit = FORM_SIZE,
    slash: Boolean = false,
    bold: Boolean = false,
    horizontal: Alignment.Horizontal = Alignment.Start,
    inline: Boolean = false,
) {
    val table = LocalTable.current
    if (forms.isEmpty()) {
        Text("—", fontSize = fontSize, color = AppColors.TextDark)
        return
    }
    if (inline) {
        // Varian sebaris dipisah koma: "е́в, е́вши" (tiap varian tetap bisa diketuk sendiri).
        Row {
            forms.forEachIndexed { i, form ->
                val text = styledForm(form, table.settings, stem)
                Text(
                    text = if (i < forms.lastIndex) text + AnnotatedString(", ") else text,
                    fontSize = fontSize, color = AppColors.TextDark, softWrap = false,
                    modifier = Modifier.clickable { table.onTap(form, info) },
                )
            }
        }
        return
    }
    Column(horizontalAlignment = horizontal) {
        forms.forEachIndexed { i, form ->
            val text = styledForm(form, table.settings, stem)
            Text(
                text = if (slash && i < forms.lastIndex) text + AnnotatedString("/") else text,
                fontSize = fontSize,
                fontWeight = if (bold && i == 0) FontWeight.Bold else FontWeight.Normal,
                color = AppColors.TextDark,
                modifier = Modifier.clickable { table.onTap(form, info) },
            )
        }
    }
}

/** Sel bergaris bawah (gaya tabel kata benda pada rancangan referensi). */
@Composable
private fun UnderlinedCell(forms: Cell, key: String, info: String, stem: String, modifier: Modifier = Modifier) {
    val highlighted = key in LocalTable.current.highlightKeys
    Column(
        modifier
            .then(if (highlighted) Modifier.background(AppColors.Highlight).testTag("highlight:$key") else Modifier)
            .padding(top = 6.dp),
    ) {
        Box(Modifier.padding(horizontal = 2.dp)) { Forms(forms, info, stem, slash = true) }
        Spacer(Modifier.height(5.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.Underline))
    }
}

@Composable
private fun HeaderLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextGray)
}

// ----------------------------------------------------- kisi berbatas (grid)

private class GridCell(
    val span: Int = 1,
    val background: Color = Color.White,
    val alignment: Alignment = Alignment.CenterStart,
    val key: String? = null,
    val content: @Composable () -> Unit,
)

/** Penampung baris tabel; `rows += listOf(sel, sel, ...)` menambah SATU baris. */
private class GridRows {
    val list = ArrayList<List<GridCell>>()

    operator fun plusAssign(row: List<GridCell>) {
        list.add(row)
    }
}

/** Tabel berbatas garis tipis dengan lebar kolom tetap + gulir mendatar bila melebihi layar. */
@Composable
private fun GridTable(widths: List<Dp>, rows: List<List<GridCell>>, modifier: Modifier = Modifier) {
    val highlightKeys = LocalTable.current.highlightKeys
    ProvideTextStyle(LocalTextStyle.current.merge(GRID_STYLE)) {
    Column(modifier.horizontalScroll(rememberScrollState()).border(0.5.dp, AppColors.GridBorder)) {
        for (row in rows) {
            Row(Modifier.height(IntrinsicSize.Min)) {
                var column = 0
                for (cell in row) {
                    val width = widths.subList(column, column + cell.span).total()
                    column += cell.span
                    val highlighted = cell.key != null && cell.key in highlightKeys
                    Box(
                        Modifier
                            .width(width)
                            .fillMaxHeight()
                            .background(if (highlighted) AppColors.Highlight else cell.background)
                            .border(0.5.dp, AppColors.GridBorder)
                            .then(if (highlighted) Modifier.testTag("highlight:${cell.key}") else Modifier)
                            .padding(horizontal = CELL_PADDING, vertical = 3.dp),
                        contentAlignment = cell.alignment,
                    ) { cell.content() }
                }
            }
        }
    }
    }
}

private fun textCell(
    text: String,
    background: Color = Color.White,
    bold: Boolean = false,
    span: Int = 1,
    alignment: Alignment = Alignment.CenterStart,
    textAlign: TextAlign = TextAlign.Start,
) = GridCell(span, background, alignment) {
    Text(
        text, fontSize = GRID_SIZE, color = AppColors.TextDark, textAlign = textAlign,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

private fun formsCell(
    forms: Cell,
    key: String?,
    info: String,
    stem: String = "",
    background: Color = Color.White,
    span: Int = 1,
    alignment: Alignment = Alignment.CenterStart,
    boldFirst: Boolean = false,
    inline: Boolean = false,
) = GridCell(span, background, alignment, key) {
    Forms(
        forms, info, stem, GRID_SIZE, bold = boldFirst,
        horizontal = if (alignment == Alignment.Center) Alignment.CenterHorizontally else Alignment.Start,
        inline = inline,
    )
}

// ------------------------------------------------------------ titik masuk

@Composable
fun ParadigmTable(paradigm: Paradigm, modifier: Modifier = Modifier) {
    when (paradigm) {
        is NounParadigm -> NounTable(paradigm, modifier)
        is AdjectiveParadigm -> AdjectiveTable(paradigm, modifier)
        is VerbParadigm -> VerbTables(paradigm, modifier)
        is SimpleParadigm -> SimpleTable(paradigm, modifier)
    }
}

// ------------------------------------------------------------ kata benda

@Composable
fun NounTable(paradigm: NounParadigm, modifier: Modifier = Modifier) {
    val stem = remember(paradigm) { commonStem(paradigm.rows.flatMap { it.sg + it.pl }) }
    Column(modifier.fillMaxWidth().testTag("table:noun")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeaderLabel("Падеж", Modifier.width(LABEL_WIDTH))
            HeaderLabel("Ед. ч.", Modifier.weight(1f))
            Spacer(Modifier.width(COLUMN_GAP))
            HeaderLabel("Мн. ч.", Modifier.weight(1f))
        }
        for (row in paradigm.rows) {
            val case = GrammarLabels.cases[row.case] ?: continue
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
                HeaderLabel(case.abbr, Modifier.width(LABEL_WIDTH).padding(top = 4.dp))
                for ((i, number) in listOf("sg", "pl").withIndex()) {
                    if (i > 0) Spacer(Modifier.width(COLUMN_GAP))
                    val label = GrammarLabels.numbers.getValue(number)
                    UnderlinedCell(
                        forms = if (number == "sg") row.sg else row.pl,
                        key = "${row.case}.$number",
                        info = "${case.abbr}, ${label.abbr} (${case.indonesian}, ${label.indonesian})",
                        stem = stem,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private val LABEL_WIDTH = 82.dp
private val COLUMN_GAP = 20.dp

// --------------------------------------------- tabel satu kolom (я, пять)

@Composable
fun SimpleTable(paradigm: SimpleParadigm, modifier: Modifier = Modifier) {
    val stem = remember(paradigm) { commonStem(paradigm.rows.flatMap { it.forms }) }
    Column(modifier.fillMaxWidth().testTag("table:simple")) {
        Row {
            HeaderLabel("Падеж", Modifier.width(LABEL_WIDTH))
            HeaderLabel("Форма", Modifier.weight(1f))
        }
        for (row in paradigm.rows) {
            val case = GrammarLabels.cases[row.case] ?: continue
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
                HeaderLabel(case.abbr, Modifier.width(LABEL_WIDTH).padding(top = 4.dp))
                UnderlinedCell(row.forms, row.case, "${case.abbr} (${case.indonesian})", stem, Modifier.weight(1f))
            }
        }
    }
}

// ------------------------------------------------------------ kata sifat

@Composable
fun AdjectiveTable(paradigm: AdjectiveParadigm, modifier: Modifier = Modifier) {
    val columns = listOf("m", "f", "n", "pl").filter { col -> paradigm.rows.any { it[col].isNotEmpty() } }
    val stem = remember(paradigm) { commonStem(paradigm.rows.flatMap { r -> columns.flatMap { r[it] } }) }
    val measure = rememberColumnMeasure()
    val widths = remember(paradigm, measure) {
        val labels = paradigm.rows.mapNotNull { GrammarLabels.cases[it.case]?.abbr } +
            listOf("Падеж", "Кратк. ф.", "Сравн. ст.", "Превосх. ст.")
        val base = listOf(measure.width(emptyList(), labels)) + columns.map { col ->
            measure.width(
                paradigm.rows.flatMap { it[col] } + (paradigm.short?.get(col) ?: emptyList()),
                listOf(GrammarLabels.genders.getValue(col).abbr),
            )
        }
        // Baris komparatif/superlatif membentang di semua kolom nilai.
        val spanning = measure.width(listOf(paradigm.comparative.joinToString(", "), paradigm.superlative.joinToString(", ")))
        val valueWidth = base.drop(1).total()
        if (spanning > valueWidth) base.dropLast(1) + (base.last() + (spanning - valueWidth)) else base
    }
    val rows = GridRows()
    rows += listOf(textCell("Падеж", AppColors.VerbLabel, bold = true)) + columns.map {
        textCell(GrammarLabels.genders.getValue(it).abbr, AppColors.VerbLabel, bold = true)
    }
    for (row in paradigm.rows) {
        val case = GrammarLabels.cases[row.case] ?: continue
        rows += listOf(textCell(case.abbr, AppColors.VerbLabel, bold = true)) + columns.map { col ->
            val gender = GrammarLabels.genders.getValue(col)
            formsCell(row[col], "${row.case}.$col", "${case.abbr}, ${gender.abbr.lowercase()} (${case.indonesian}, ${gender.indonesian})", stem)
        }
    }
    paradigm.short?.let { short ->
        rows += listOf(textCell("Кратк. ф.", AppColors.VerbLabel, bold = true)) + columns.map { col ->
            val gender = GrammarLabels.genders.getValue(col)
            formsCell(short[col], "short.$col", "Кратк. ф., ${gender.abbr.lowercase()} (bentuk pendek, ${gender.indonesian})", stem)
        }
    }
    if (paradigm.comparative.isNotEmpty()) {
        rows += listOf(
            textCell("Сравн. ст.", AppColors.VerbLabel, bold = true),
            formsCell(paradigm.comparative, "comp", "Сравн. ст. (komparatif: lebih …)", span = columns.size, alignment = Alignment.Center, inline = true),
        )
    }
    if (paradigm.superlative.isNotEmpty()) {
        rows += listOf(
            textCell("Превосх. ст.", AppColors.VerbLabel, bold = true),
            formsCell(paradigm.superlative, "sup", "Превосх. ст. (superlatif: paling …)", span = columns.size, alignment = Alignment.Center, inline = true),
        )
    }
    GridTable(widths, rows.list, modifier.testTag("table:adj"))
}

// ------------------------------------------------------------ kata kerja

@Composable
fun VerbTables(paradigm: VerbParadigm, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        VerbTable(paradigm)
        paradigm.fullTable?.let { FullVerbTables(it) }
    }
}

@Composable
private fun VerbTable(paradigm: VerbParadigm) {
    // Kolom kala: НСВ → наст.; СВ → будущ.; dua aspek (atau быть) → keduanya.
    val tenses = buildList {
        paradigm.present?.let { add("pres" to it) }
        paradigm.future?.let { add("fut" to it) }
    }
    val conjugated = tenses.flatMap { (_, forms) -> GrammarLabels.personOrder.flatMap { forms[it] } }
    val presentStem = remember(paradigm) { commonStem(conjugated) }
    val past = paradigm.past
    val pastStem = remember(paradigm) { commonStem(past.m + past.f + past.n + past.pl) }
    val imperative = paradigm.imperative
    val participles = paradigm.participles
    val compoundFuture = paradigm.future == null && paradigm.present != null
    val participleRows = buildList {
        val all = listOf(
            "pres_act" to participles.presAct, "past_act" to participles.pastAct,
            "pres_adv" to participles.presAdv, "past_adv" to participles.pastAdv,
            "pres_pass" to participles.presPass, "past_pass" to participles.pastPass,
        )
        // НСВ: keenam baris selalu tampil; СВ: hanya bentuk lampau (kala kini tidak ada).
        val always = if (paradigm.aspect == "pf") setOf("past_act", "past_adv", "past_pass") else all.map { it.first }.toSet()
        for ((key, forms) in all) if (key in always || forms.isNotEmpty()) add(key to forms)
    }
    val measure = rememberColumnMeasure()
    val widths = remember(paradigm, measure) {
        val spanning = participleRows.map { it.second.joinToString(", ") } +
            if (compoundFuture) paradigm.infinitive.take(1).map { "буду/будешь… $it" } else emptyList()
        val participleWidth = measure.width(spanning)
        val labels = GrammarLabels.personOrder.map { GrammarLabels.persons.getValue(it).abbr } +
            participleRows.map { GrammarLabels.participles.getValue(it.first).abbr } +
            if (compoundFuture) listOf("Будущее") else emptyList()
        val base = buildList {
            add(measure.width(labels))
            for ((key, forms) in tenses) {
                add(measure.width(GrammarLabels.personOrder.flatMap { forms[it] }, listOf(GrammarLabels.tenses.getValue(key).abbr)))
            }
            add(measure.width(past.m + past.f + past.n + past.pl, listOf("прош.")))
            add(measure.width(imperative.sg + imperative.pl + "—", listOf("повелит.")))
        }
        // Baris partisip membentang di semua kolom nilai: lebarkan kolom terakhir bila perlu.
        val valueWidth = base.drop(1).total()
        if (participleWidth > valueWidth) base.dropLast(1) + (base.last() + (participleWidth - valueWidth)) else base
    }

    val rows = GridRows()
    rows += buildList {
        add(textCell("", AppColors.VerbLabel))
        for ((key, _) in tenses) add(textCell(GrammarLabels.tenses.getValue(key).abbr, AppColors.VerbLabel, bold = true, alignment = Alignment.Center))
        add(textCell("прош.", AppColors.VerbLabel, bold = true, alignment = Alignment.Center))
        add(textCell("повелит.", AppColors.VerbLabel, bold = true, alignment = Alignment.Center))
    }
    for (person in GrammarLabels.personOrder) {
        val label = GrammarLabels.persons.getValue(person)
        val plural = person.startsWith("pl")
        rows += buildList {
            add(textCell(label.abbr, AppColors.VerbLabel, alignment = Alignment.CenterEnd, textAlign = TextAlign.End))
            for ((key, forms) in tenses) {
                val tense = GrammarLabels.tenses.getValue(key)
                add(formsCell(forms[person], "$key.$person", "${tense.abbr}, ${label.russian} (${tense.indonesian}, ${label.indonesian})", presentStem))
            }
            add(pastCell(paradigm, person, plural, pastStem))
            add(
                when (person) {
                    "sg2" -> formsCell(imperative.sg, "imp.sg", "Повелит., ед. ч. (imperatif, tunggal)", presentStem)
                    "pl2" -> formsCell(imperative.pl, "imp.pl", "Повелит., мн. ч. (imperatif, jamak/sopan)", presentStem)
                    else -> textCell("—")
                },
            )
        }
    }
    val valueSpan = widths.size - 1
    for ((key, forms) in participleRows) {
        val label = GrammarLabels.participles.getValue(key)
        rows += listOf(
            textCell(label.abbr, AppColors.VerbLabel, alignment = Alignment.CenterEnd, textAlign = TextAlign.End),
            formsCell(forms, "part.$key", "${label.abbr} (${label.indonesian})", span = valueSpan, alignment = Alignment.Center, inline = true),
        )
    }
    if (compoundFuture) {
        val infinitive = paradigm.infinitive.firstOrNull().orEmpty()
        rows += listOf(
            textCell("Будущее", AppColors.VerbLabel, alignment = Alignment.CenterEnd, textAlign = TextAlign.End),
            formsCell(
                listOf("буду/будешь… $infinitive"), null,
                "Будущее (kala depan majemuk: буду, будешь, будет, будем, будете, будут + infinitif)",
                span = valueSpan, alignment = Alignment.Center,
            ),
        )
    }
    GridTable(widths, rows.list, Modifier.testTag("table:verb"))
}

private fun pastCell(paradigm: VerbParadigm, person: String, plural: Boolean, stem: String): GridCell {
    val past = paradigm.past
    val highlightKey: String?
    val lines: List<Pair<Cell, String>>
    if (plural) {
        lines = listOf(past.pl to "Прош., мн. ч. (kala lampau, jamak)")
        highlightKey = "past.pl"
    } else {
        lines = buildList {
            add(past.m to "Прош., муж., ед. ч. (kala lampau, maskulin)")
            add(past.f to "Прош., жен., ед. ч. (kala lampau, feminin)")
            if (person == "sg3") add(past.n to "Прош., ср., ед. ч. (kala lampau, netral)")
        }
        highlightKey = null
    }
    return GridCell(key = highlightKey) {
        val table = LocalTable.current
        Column {
            val genders = listOf("past.m", "past.f", "past.n")
            lines.forEachIndexed { i, (forms, info) ->
                val highlighted = !plural && genders[i] in table.highlightKeys
                Box(if (highlighted) Modifier.background(AppColors.Highlight).testTag("highlight:${genders[i]}") else Modifier) {
                    Forms(forms, info, stem, GRID_SIZE)
                }
            }
        }
    }
}

// ----------------------------------- tabel lengkap быть (rancangan referensi)

@Composable
private fun FullVerbTables(table: FullVerbTable) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (table.persons.isNotEmpty()) {
            val header = AppColors.PastelGreen
            val widths = listOf(76.dp, 74.dp, 72.dp, 96.dp, 96.dp, 96.dp, 96.dp, 74.dp, 92.dp)
            val rows = GridRows()
            rows += listOf(textCell("Время:", header, bold = true)) +
                listOf("Прошедшее", "Настоящее", "Будущее", "Повелит. накл.").map {
                    textCell(it, header, bold = true, span = 2, alignment = Alignment.Center, textAlign = TextAlign.Center)
                }
            rows += listOf(textCell("Число:", header, bold = true)) +
                List(4) { listOf("ед.", "мн.") }.flatten().map { textCell(it, header, bold = true, alignment = Alignment.Center) }
            table.persons.forEachIndexed { index, row ->
                val background = if (index % 2 == 1) AppColors.RowAlt else Color.White
                val person = row.label
                rows += listOf(
                    textCell(person, header),
                    formsCell(row.pastSg, null, "Прошедшее, ед. ч. ($person)", background = background),
                    formsCell(row.pastPl, null, "Прошедшее, мн. ч. ($person)", background = background),
                    formsCell(row.presSg, null, "Настоящее, ед. ч. ($person)", background = background, boldFirst = true),
                    formsCell(row.presPl, null, "Настоящее, мн. ч. ($person)", background = background, boldFirst = true),
                    formsCell(row.futSg, null, "Будущее, ед. ч. ($person)", background = background),
                    formsCell(row.futPl, null, "Будущее, мн. ч. ($person)", background = background),
                    formsCell(row.impSg, null, "Повелит. накл., ед. ч. ($person)", background = background),
                    formsCell(row.impPl, null, "Повелит. накл., мн. ч. ($person)", background = background),
                )
            }
            GridTable(widths, rows.list, Modifier.testTag("table:full-tenses"))
        }
        if (table.participles.isNotEmpty()) {
            val header = AppColors.PastelGreen
            val widths = listOf(236.dp, 104.dp, 104.dp, 104.dp, 104.dp)
            val rows = GridRows()
            rows += listOf(textCell("Род/число:", header, bold = true, alignment = Alignment.CenterEnd, textAlign = TextAlign.End)) +
                listOf("муж.", "жен.", "ср.", "мн. ч.").map { textCell(it, header, bold = true, alignment = Alignment.Center) }
            for (row in table.participles) {
                val label = textCell(row.label, header)
                rows += if (row.all.isNotEmpty()) {
                    listOf(label, formsCell(listOf(row.all.joinToString(", ")), null, row.label, span = 4))
                } else {
                    listOf(
                        label,
                        formsCell(row.m, null, "${row.label}, муж."),
                        formsCell(row.f, null, "${row.label}, жен."),
                        formsCell(row.n, null, "${row.label}, ср."),
                        formsCell(row.pl, null, "${row.label}, мн. ч."),
                    )
                }
            }
            GridTable(widths, rows.list, Modifier.testTag("table:full-participles"))
        }
        if (table.footnotes.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (note in table.footnotes) {
                    Text(note, fontSize = 13.sp, color = AppColors.TextGray, lineHeight = 17.sp)
                }
            }
        }
    }
}
