package com.izucai.morphology.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Bookmark(val lemmaId: Long, val lemma: String, val translation: String)

data class DisplaySettings(
    val stressStyle: StressStyle = StressStyle.ACUTE,
    /** Warnai akhiran kata (bagian yang berubah) berbeda dari akar kata. */
    val highlightEndings: Boolean = true,
)

/** Riwayat pencarian, kata tersimpan, dan pengaturan tampilan -- semuanya lokal di perangkat. */
class UserPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun history(): List<String> = decode<List<String>>(KEY_HISTORY) ?: emptyList()

    fun addHistory(query: String): List<String> {
        val updated = (listOf(query) + history().filter { it != query }).take(MAX_HISTORY)
        prefs.edit().putString(KEY_HISTORY, json.encodeToString(updated)).apply()
        return updated
    }

    fun clearHistory() = prefs.edit().remove(KEY_HISTORY).apply()

    fun bookmarks(): List<Bookmark> = decode<List<Bookmark>>(KEY_BOOKMARKS) ?: emptyList()

    /** Simpan bila belum ada, hapus bila sudah ada. Mengembalikan daftar terbaru. */
    fun toggleBookmark(bookmark: Bookmark): List<Bookmark> {
        val current = bookmarks()
        val updated = if (current.any { it.lemmaId == bookmark.lemmaId }) {
            current.filter { it.lemmaId != bookmark.lemmaId }
        } else {
            listOf(bookmark) + current
        }
        prefs.edit().putString(KEY_BOOKMARKS, json.encodeToString(updated)).apply()
        return updated
    }

    fun settings() = DisplaySettings(
        stressStyle = runCatching { StressStyle.valueOf(prefs.getString(KEY_STRESS, null) ?: "") }
            .getOrDefault(StressStyle.ACUTE),
        highlightEndings = prefs.getBoolean(KEY_ENDINGS, true),
    )

    fun saveSettings(settings: DisplaySettings) = prefs.edit()
        .putString(KEY_STRESS, settings.stressStyle.name)
        .putBoolean(KEY_ENDINGS, settings.highlightEndings)
        .apply()

    private inline fun <reified T> decode(key: String): T? =
        prefs.getString(key, null)?.let { runCatching { json.decodeFromString<T>(it) }.getOrNull() }

    private companion object {
        const val KEY_HISTORY = "history"
        const val KEY_BOOKMARKS = "bookmarks"
        const val KEY_STRESS = "stress_style"
        const val KEY_ENDINGS = "highlight_endings"
        const val MAX_HISTORY = 30
    }
}
