package com.izucai.morphology.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import com.izucai.morphology.data.TextNormalizer
import java.util.Locale

/**
 * Pelafalan offline memakai mesin Text-to-Speech bawaan perangkat (locale ru-RU).
 * Bila suara bahasa Rusia belum terpasang, [speak] mengembalikan false agar UI bisa memberi tahu pengguna.
 */
class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val tts = TextToSpeech(context.applicationContext, this)

    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val result = tts.setLanguage(Locale.forLanguageTag("ru-RU"))
        ready = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
        if (ready) tts.setSpeechRate(0.9f)
    }

    /** @return false bila suara Rusia tidak tersedia di perangkat. */
    fun speak(form: String): Boolean {
        if (!ready) return false
        val text = TextNormalizer.speakable(form)
        if (text.isEmpty()) return true
        return tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "form") == TextToSpeech.SUCCESS
    }

    fun shutdown() {
        tts.stop()
        tts.shutdown()
    }
}
