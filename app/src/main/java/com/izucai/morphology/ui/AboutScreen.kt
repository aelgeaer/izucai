package com.izucai.morphology.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.izucai.morphology.data.DisplaySettings
import com.izucai.morphology.data.StressStyle
import com.izucai.morphology.data.ThemeMode

/** Tab О ПРОГРАММЕ: informasi aplikasi, statistik basis data offline, dan pengaturan tampilan. */
@Composable
fun AboutScreen(
    stats: Map<String, String>,
    settings: DisplaySettings,
    versionName: String,
    onSettingsChange: (DisplaySettings) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("about-screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text("Изучай", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = AppColors.Teal)
            Text("Morfologi bahasa Rusia · versi $versionName", fontSize = 14.sp, color = AppColors.TextMuted)
            Spacer(Modifier.height(8.dp))
            Text(
                "Kamus perubahan kata Rusia–Indonesia: deklinasi kata benda, kata sifat, pronomina dan numeralia, " +
                    "serta konjugasi kata kerja. Masukkan kata dalam bentuk apa pun — aplikasi menemukan kata dasarnya. " +
                    "Seluruh data tersimpan di perangkat; aplikasi tidak memerlukan dan tidak meminta akses internet.",
                fontSize = 15.sp, color = AppColors.TextDark, lineHeight = 22.sp,
            )
        }

        Section("Pengaturan tampilan") {
            Text("Tema", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextDark)
            for ((mode, label) in listOf(
                ThemeMode.SYSTEM to "Sesuai sistem",
                ThemeMode.LIGHT to "Terang",
                ThemeMode.DARK to "Gelap",
            )) {
                RadioRow(settings.themeMode == mode, label, "theme:${mode.name}") {
                    onSettingsChange(settings.copy(themeMode = mode))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("Tanda tekan (ударение)", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextDark)
            for ((style, label) in listOf(
                StressStyle.ACUTE to "Aksen di atas huruf — заказа́л",
                StressStyle.APOSTROPHE to "Apostrof setelah vokal — заказа'л",
                StressStyle.NONE to "Tanpa tanda tekan — заказал",
            )) {
                RadioRow(settings.stressStyle == style, label, "stress:${style.name}") {
                    onSettingsChange(settings.copy(stressStyle = style))
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Warnai akhiran kata", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextDark)
                    Text(
                        "Akar kata tetap berwarna biasa; bagian yang berubah (akhiran kasus/persona) diberi warna.",
                        fontSize = 13.sp, color = AppColors.TextGray, lineHeight = 18.sp,
                    )
                }
                Switch(
                    checked = settings.highlightEndings,
                    onCheckedChange = { onSettingsChange(settings.copy(highlightEndings = it)) },
                    modifier = Modifier.testTag("toggle-endings"),
                    colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Teal, uncheckedTrackColor = AppColors.Surface),
                )
            }
        }

        Section("Basis data offline") {
            if (stats.isEmpty()) {
                Text("Memuat…", fontSize = 15.sp, color = AppColors.TextGray)
            } else {
                Stat("Jumlah kata dasar (lema)", stats["lemma_count"])
                Stat("  kata benda", stats["count_NOUN"])
                Stat("  kata kerja", stats["count_VERB"])
                Stat("  kata sifat", stats["count_ADJ"])
                Stat("  pronomina", stats["count_PRON"])
                Stat("  numeralia", stats["count_NUM"])
                Stat("Bentuk kata yang dikenali", stats["form_count"])
                Stat("Kata berterjemahan Indonesia", stats["translated_count"])
                Stat("Tanggal pembuatan data", stats["build_date"], number = false)
                Text(
                    "Kata di luar kamus kurasi Indonesia tetap punya tabel perubahan lengkap; artinya ditampilkan dalam bahasa Inggris (EN).",
                    Modifier.padding(top = 6.dp), fontSize = 13.sp, color = AppColors.TextGray, lineHeight = 18.sp,
                )
            }
        }

        Section("Sumber data & lisensi") {
            Text(
                "Tabel deklinasi dan konjugasi beserta tanda tekan: Wiktionary (en.wiktionary.org) melalui " +
                    "ekstraksi Kaikki.org — lisensi CC BY-SA 4.0. Kosakata modern yang belum ada di sana " +
                    "(зачётка, пересдача, хакатон, …) dilengkapi dari ru.wiktionary.org (CC BY-SA 4.0).\n" +
                    "Urutan frekuensi kata: FrequencyWords (korpus OpenSubtitles 2018) — CC BY-SA 4.0.\n" +
                    "Terjemahan Indonesia: kamus kurasi aplikasi ini.",
                fontSize = 14.sp, color = AppColors.TextGray, lineHeight = 20.sp,
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

/** Satu pilihan radio; seluruh baris bisa diketuk dan terbaca sebagai pilihan oleh pembaca layar. */
@Composable
private fun RadioRow(selected: Boolean, label: String, tag: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            modifier = Modifier.padding(12.dp),
            colors = RadioButtonDefaults.colors(selectedColor = AppColors.Teal, unselectedColor = AppColors.TextGray),
        )
        Text(label, fontSize = 15.sp, color = AppColors.TextDark)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(AppColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, Modifier.padding(bottom = 4.dp), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = AppColors.TextDark)
        content()
    }
}

@Composable
private fun Stat(label: String, value: String?, number: Boolean = true) {
    if (value == null) return
    val shown = if (number) value.toLongOrNull()?.let { "%,d".format(it).replace(',', '.') } ?: value else value
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), fontSize = 15.sp, color = AppColors.TextDark)
        Text(shown, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = AppColors.TextDark)
    }
}
