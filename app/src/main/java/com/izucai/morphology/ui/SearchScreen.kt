package com.izucai.morphology.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izucai.morphology.data.Bookmark
import com.izucai.morphology.data.DisplaySettings
import com.izucai.morphology.data.MatchType
import com.izucai.morphology.data.NounParadigm
import com.izucai.morphology.data.QueryLanguage
import com.izucai.morphology.data.SearchOutcome
import com.izucai.morphology.data.SearchResultItem
import com.izucai.morphology.data.Suggestion
import com.izucai.morphology.data.TextNormalizer
import com.izucai.morphology.data.VerbParadigm

/** Tab ПОИСК: kolom pencarian, saran, hasil (kartu tabel), riwayat & kata tersimpan. */
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    onToggleBookmark: (SearchResultItem) -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit,
    onClearHistory: () -> Unit,
    onFormTap: (form: String, info: String) -> Unit,
) {
    val focus = LocalFocusManager.current
    val submit: (String) -> Unit = { text ->
        focus.clearFocus()
        onSearch(text)
    }
    Column(Modifier.fillMaxSize()) {
        SearchBar(state.query, onQueryChange, onSubmit = { submit(state.query) }, onClear = onClear)
        if (state.suggestions.isNotEmpty()) {
            SuggestionList(state.suggestions, state.settings, onPick = { submit(it.text) })
        }
        when {
            state.error != null -> Message(state.error)
            state.isLoading -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Teal)
            }
            state.outcome == null -> StartContent(state, submit, onRemoveBookmark, onClearHistory)
            else -> Results(state.outcome, state, submit, onToggleBookmark, onFormTap)
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onSubmit: () -> Unit, onClear: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f).testTag("search-field"),
            singleLine = true,
            textStyle = TextStyle(fontSize = 20.sp, color = AppColors.TextDark),
            placeholder = {
                Text(
                    "Ketik kata Rusia (bentuk apa saja) atau Indonesia…",
                    fontSize = 15.sp, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            },
            trailingIcon = if (query.isEmpty()) null else {
                {
                    Text(
                        "✕", fontSize = 18.sp, color = AppColors.TextGray,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClear).padding(8.dp).testTag("clear-button"),
                    )
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = AppColors.Teal,
                unfocusedIndicatorColor = AppColors.Teal,
                cursorColor = AppColors.Teal,
            ),
        )
        Spacer(Modifier.width(14.dp))
        Button(
            onClick = onSubmit,
            modifier = Modifier.testTag("search-button"),
            shape = RoundedCornerShape(3.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AppColors.ButtonGray, contentColor = AppColors.TextDark),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
        ) {
            Text("НАЙТИ", fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
        }
    }
}

@Composable
private fun SuggestionList(suggestions: List<Suggestion>, settings: DisplaySettings, onPick: (Suggestion) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("suggestions"),
        shape = RoundedCornerShape(6.dp), shadowElevation = 3.dp, color = Color.White,
    ) {
        Column {
            suggestions.forEachIndexed { i, suggestion ->
                if (i > 0) HorizontalDivider(color = Color(0xFFEEEEEE))
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(suggestion) }.padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        TextNormalizer.displayStress(suggestion.text, settings.stressStyle),
                        fontSize = 17.sp, color = AppColors.TextDark, fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        TextNormalizer.displayStress(suggestion.hint, settings.stressStyle),
                        fontSize = 14.sp, color = AppColors.TextMuted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(text, Modifier.padding(20.dp), fontSize = 16.sp, color = AppColors.TextGray, lineHeight = 22.sp)
}

// ----------------------------------------------- awal: riwayat & tersimpan

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartContent(
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit,
    onClearHistory: () -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().testTag("start-content"), contentPadding = PaddingValues(16.dp)) {
        if (state.history.isEmpty() && state.bookmarks.isEmpty()) {
            item {
                Text(
                    "Ketik kata dalam bentuk apa pun — mis. машинами, заказала, ели — atau kata Indonesia " +
                        "seperti mobil, makan, memesan. Aplikasi akan menemukan kata dasarnya dan menampilkan " +
                        "seluruh tabel perubahannya. Semua bekerja tanpa internet.",
                    fontSize = 15.sp, color = AppColors.TextGray, lineHeight = 22.sp,
                )
                Spacer(Modifier.height(14.dp))
                SectionTitle("Coba cari")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (example in listOf("машинами", "есть", "заказала", "новую", "mobil", "makan", "memesan")) {
                        Chip(example) { onSearch(example) }
                    }
                }
            }
        }
        if (state.bookmarks.isNotEmpty()) {
            item { SectionTitle("★ Kata tersimpan") }
            items(state.bookmarks, key = { "b${it.lemmaId}" }) { bookmark ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSearch(TextNormalizer.stripStress(bookmark.lemma)) }.padding(vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            TextNormalizer.displayStress(bookmark.lemma, state.settings.stressStyle),
                            fontSize = 18.sp, color = AppColors.TextDark, fontWeight = FontWeight.Medium,
                        )
                        if (bookmark.translation.isNotEmpty()) {
                            Text(bookmark.translation, fontSize = 14.sp, color = AppColors.TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        "★", fontSize = 22.sp, color = AppColors.Teal,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onRemoveBookmark(bookmark) }.padding(8.dp),
                    )
                }
                HorizontalDivider(color = Color(0xFFE6E6E6))
            }
            item { Spacer(Modifier.height(18.dp)) }
        }
        if (state.history.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { SectionTitle("Terakhir dicari") }
                    Text(
                        "Hapus riwayat", fontSize = 14.sp, color = AppColors.Teal,
                        modifier = Modifier.clickable(onClick = onClearHistory).padding(6.dp),
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (query in state.history) Chip(query) { onSearch(query) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, Modifier.padding(bottom = 8.dp), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AppColors.TextGray)
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.TealSoft)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        fontSize = 16.sp, color = AppColors.TextDark,
    )
}

// ------------------------------------------------------------------ hasil

@Composable
private fun Results(
    outcome: SearchOutcome,
    state: SearchUiState,
    onSearch: (String) -> Unit,
    onToggleBookmark: (SearchResultItem) -> Unit,
    onFormTap: (String, String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(outcome) { listState.scrollToItem(0) }
    if (outcome.items.isEmpty()) {
        Column(Modifier.padding(20.dp).testTag("no-results")) {
            Text("Tidak ditemukan: «${outcome.query}»", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextDark)
            Spacer(Modifier.height(6.dp))
            Text(
                if (outcome.language == QueryLanguage.RUSSIAN) {
                    "Kata ini belum ada di kamus offline. Periksa ejaannya, atau coba bentuk lain dari kata tersebut."
                } else {
                    "Belum ada terjemahan Indonesia yang cocok. Coba sinonimnya, atau ketik kata Rusianya langsung."
                },
                fontSize = 15.sp, color = AppColors.TextGray, lineHeight = 21.sp,
            )
            if (outcome.alternatives.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Mungkin maksud Anda")
                for (alt in outcome.alternatives) {
                    Row(Modifier.fillMaxWidth().clickable { onSearch(alt.text) }.padding(vertical = 8.dp)) {
                        Text(TextNormalizer.displayStress(alt.text, state.settings.stressStyle), fontSize = 17.sp, color = AppColors.Teal, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.width(10.dp))
                        Text(TextNormalizer.displayStress(alt.hint, state.settings.stressStyle), fontSize = 15.sp, color = AppColors.TextGray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        return
    }
    val bookmarked = remember(state.bookmarks) { state.bookmarks.mapTo(HashSet()) { it.lemmaId } }
    LazyColumn(
        Modifier.fillMaxSize().testTag("results"),
        state = listState,
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        if (outcome.items.size > 1) {
            item {
                Text(
                    "${outcome.items.size} kata cocok dengan «${outcome.query}»",
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).testTag("result-count"),
                    fontSize = 14.sp, color = AppColors.TextMuted,
                )
                val homonyms = outcome.items.groupBy { it.lemma.lemmaClean to it.lemma.pos }.any { it.value.size > 1 }
                if (homonyms) {
                    Text(
                        "Ada kata dengan ejaan sama tetapi arti berbeda (homonim). " +
                            "Bedakan dari terjemahan, aspek, dan pasangan aspeknya.",
                        Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp).testTag("homonym-note"),
                        fontSize = 13.sp, color = AppColors.TextGray, lineHeight = 18.sp,
                    )
                }
            }
        }
        items(outcome.items, key = { it.lemma.id }) { item ->
            ResultCard(item, state.settings, item.lemma.id in bookmarked, onSearch, onToggleBookmark, onFormTap)
            HorizontalDivider(Modifier.padding(top = 22.dp), thickness = 8.dp, color = Color(0xFFEDEDED))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultCard(
    item: SearchResultItem,
    settings: DisplaySettings,
    bookmarked: Boolean,
    onSearch: (String) -> Unit,
    onToggleBookmark: (SearchResultItem) -> Unit,
    onFormTap: (String, String) -> Unit,
) {
    val lemma = item.lemma
    val show: (String) -> String = { TextNormalizer.displayStress(it, settings.stressStyle) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 14.dp).testTag("card:${lemma.lemmaClean}:${lemma.pos}")) {
        // Kata dasar + tombol simpan & lafal
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                show(lemma.lemmaStressed),
                modifier = Modifier.clickable { onFormTap(lemma.lemmaStressed, "Kata dasar · ${GrammarLabels.posLabel(lemma.pos)}") }.testTag("lemma-title"),
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AppColors.TextDark,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "🔊", fontSize = 18.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onFormTap(lemma.lemmaStressed, "Kata dasar · ${GrammarLabels.posLabel(lemma.pos)}") }.padding(6.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (bookmarked) "★" else "☆", fontSize = 26.sp, color = AppColors.Teal,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onToggleBookmark(item) }.padding(6.dp).testTag("bookmark-button"),
            )
        }
        // Kelas kata, gender, keanimasian
        val details = buildList {
            add(GrammarLabels.posLabel(lemma.pos))
            lemma.gender?.let { add(GrammarLabels.genderIndonesian(it)) }
            (item.paradigm as? NounParadigm)?.let { noun ->
                when (noun.animacy) {
                    "одуш." -> add("одуш. (bernyawa)")
                    "неодуш." -> add("неодуш. (tak bernyawa)")
                }
                if (noun.indeclinable) add("несклоняемое (tidak berubah bentuk)")
                when (noun.number) {
                    "sg_only" -> add("hanya tunggal")
                    "pl_only" -> add("hanya jamak")
                }
            }
        }
        Text(details.joinToString("  ·  "), fontSize = 13.sp, color = AppColors.TextMuted)
        Spacer(Modifier.height(6.dp))
        // Terjemahan
        if (lemma.translationsId.isNotEmpty()) {
            LanguageLine("ID", lemma.translationsId, AppColors.Teal, bold = true, tag = "translation-id")
        } else if (lemma.translationsEn.isNotEmpty()) {
            LanguageLine("EN", lemma.translationsEn, AppColors.TextMuted, bold = false, tag = "translation-en")
            Text("Terjemahan Indonesia belum tersedia untuk kata ini.", fontSize = 12.sp, color = AppColors.TextMuted)
        }
        // Badge: masukan berupa bentuk yang sudah berubah / kata Indonesia
        if (item.matchType == MatchType.INFLECTED && item.matchedForms.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(AppColors.Highlight)
                    .padding(horizontal = 10.dp, vertical = 7.dp).testTag("inflected-badge"),
            ) {
                for (form in item.matchedForms.distinct().take(3)) {
                    Text(
                        buildAnnotatedString {
                            append("Ditemukan dari bentuk: ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(show(form.formStressed)) }
                            append(" (${form.gramInfo})")
                        },
                        fontSize = 14.sp, color = AppColors.TextDark, lineHeight = 19.sp,
                    )
                }
                Text(
                    buildAnnotatedString {
                        append("→ Kata dasar: ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(show(lemma.lemmaStressed)) }
                    },
                    fontSize = 14.sp, color = AppColors.TextDark,
                )
            }
        } else if (item.matchType == MatchType.INDONESIAN && item.matchedIndonesian != null) {
            Text("Cocok dengan kata Indonesia «${item.matchedIndonesian}»", Modifier.padding(top = 4.dp), fontSize = 13.sp, color = AppColors.TextMuted)
        }
        // Aspek kata kerja + pasangan aspek
        (item.paradigm as? VerbParadigm)?.let { verb ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.testTag("aspect")) {
                Text("Aspect:", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextGray)
                Spacer(Modifier.width(12.dp))
                Text(lemma.aspect.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.TextGray)
            }
            Text(GrammarLabels.aspectIndonesian(verb.aspect), fontSize = 13.sp, color = AppColors.TextMuted)
            if (verb.partners.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pasangan aspek:", Modifier.padding(top = 6.dp), fontSize = 14.sp, color = AppColors.TextGray)
                    for (partner in verb.partners) Chip(show(partner)) { onSearch(TextNormalizer.stripStress(partner)) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        CompositionLocalProvider(LocalTable provides TableContext(settings, item.highlightKeys, onFormTap)) {
            ParadigmTable(item.paradigm)
        }
    }
}

@Composable
private fun LanguageLine(code: String, text: String, color: Color, bold: Boolean, tag: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            code,
            modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(3.dp)).background(color).padding(horizontal = 5.dp, vertical = 1.dp),
            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text, Modifier.testTag(tag),
            fontSize = 17.sp, color = AppColors.TextDark, lineHeight = 23.sp,
            fontWeight = if (bold) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
