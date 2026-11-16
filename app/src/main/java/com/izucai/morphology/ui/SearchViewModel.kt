package com.izucai.morphology.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.izucai.morphology.data.AndroidSqlRunner
import com.izucai.morphology.data.Bookmark
import com.izucai.morphology.data.DisplaySettings
import com.izucai.morphology.data.MorphologyDao
import com.izucai.morphology.data.MorphologyDatabase
import com.izucai.morphology.data.MorphologyRepository
import com.izucai.morphology.data.SearchOutcome
import com.izucai.morphology.data.SearchResultItem
import com.izucai.morphology.data.Suggestion
import com.izucai.morphology.data.UserPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    /** Hasil pencarian terakhir; null = belum mencari (tampilkan riwayat & kata tersimpan). */
    val outcome: SearchOutcome? = null,
    val suggestions: List<Suggestion> = emptyList(),
    val history: List<String> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    val settings: DisplaySettings = DisplaySettings(),
    val stats: Map<String, String> = emptyMap(),
    val error: String? = null,
) {
    /** Daftar kartu tabel yang ditampilkan berurutan (bisa lebih dari satu untuk homonim). */
    val results: List<SearchResultItem> get() = outcome?.items.orEmpty()
}

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = UserPrefs(application)
    private val repository = CompletableDeferred<MorphologyRepository>()
    private val _state = MutableStateFlow(
        SearchUiState(history = prefs.history(), bookmarks = prefs.bookmarks(), settings = prefs.settings()),
    )
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var suggestJob: Job? = null
    private var searchJob: Job? = null

    init {
        // Menyalin basis data dari assets (sekali saja) dan membukanya di luar Main Thread.
        viewModelScope.launch {
            try {
                val repo = withContext(Dispatchers.IO) {
                    MorphologyRepository(MorphologyDao(AndroidSqlRunner(MorphologyDatabase.open(application))))
                }
                repository.complete(repo)
                _state.update { it.copy(stats = repo.stats()) }
            } catch (e: Exception) {
                repository.completeExceptionally(e)
                _state.update { it.copy(error = "Basis data offline gagal dibuka: ${e.message}") }
            }
        }
    }

    fun onQueryChange(text: String) {
        _state.update {
            it.copy(query = text, outcome = if (text.isBlank()) null else it.outcome, suggestions = if (text.isBlank()) emptyList() else it.suggestions)
        }
        suggestJob?.cancel()
        if (text.isBlank()) return
        suggestJob = viewModelScope.launch {
            delay(SUGGEST_DEBOUNCE_MS)
            val suggestions = runCatching { repository.await().suggest(text) }.getOrDefault(emptyList())
            _state.update { if (it.query == text) it.copy(suggestions = suggestions) else it }
        }
    }

    /** Jalankan pencarian untuk teks di kolom pencarian (atau [text] bila diberikan). */
    fun search(text: String = _state.value.query) {
        val query = text.trim()
        if (query.isEmpty()) return
        suggestJob?.cancel()
        searchJob?.cancel()
        _state.update { it.copy(query = query, isLoading = true, suggestions = emptyList(), error = null) }
        searchJob = viewModelScope.launch {
            try {
                val outcome = repository.await().search(query)
                val history = if (outcome.items.isNotEmpty()) prefs.addHistory(query) else _state.value.history
                _state.update { it.copy(isLoading = false, outcome = outcome, history = history) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Pencarian gagal: ${e.message}") }
            }
        }
    }

    /** Buka satu lema tertentu (dari saran, kata tersimpan, atau pasangan aspek). */
    fun openSuggestion(suggestion: Suggestion) = search(suggestion.text)

    fun clearQuery() {
        suggestJob?.cancel()
        _state.update { it.copy(query = "", outcome = null, suggestions = emptyList()) }
    }

    fun clearHistory() {
        prefs.clearHistory()
        _state.update { it.copy(history = emptyList()) }
    }

    fun toggleBookmark(item: SearchResultItem) {
        val lemma = item.lemma
        val bookmark = Bookmark(lemma.id, lemma.lemmaStressed, lemma.translationsId.ifEmpty { lemma.translationsEn })
        _state.update { it.copy(bookmarks = prefs.toggleBookmark(bookmark)) }
    }

    fun removeBookmark(bookmark: Bookmark) {
        _state.update { it.copy(bookmarks = prefs.toggleBookmark(bookmark)) }
    }

    fun updateSettings(settings: DisplaySettings) {
        prefs.saveSettings(settings)
        _state.update { it.copy(settings = settings) }
    }

    private companion object {
        const val SUGGEST_DEBOUNCE_MS = 150L
    }
}
