package com.izucai.morphology.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private class CaseGuide(
    val abbr: String,
    val name: String,
    val indonesian: String,
    val questions: String,
    val usage: String,
    val example: String,
    val translation: String,
)

private val CASES = listOf(
    CaseGuide(
        "Им.", "Именительный падеж", "Nominatif", "Кто? Что? (siapa? apa?)",
        "Bentuk kamus. Dipakai untuk subjek kalimat dan untuk menyebut nama sesuatu.",
        "Маши́на стои́т во дворе́.", "Mobil itu berada di halaman.",
    ),
    CaseGuide(
        "Род.", "Родительный падеж", "Genitif", "Кого? Чего? (dari siapa? dari apa?)",
        "Kepemilikan (\"milik …\"), ketiadaan (нет + genitif), jumlah (setelah angka dan много/мало), " +
            "serta setelah preposisi у, из, от, до, без, для, после.",
        "У меня́ нет маши́ны.", "Saya tidak punya mobil.",
    ),
    CaseGuide(
        "Дат.", "Дательный падеж", "Datif", "Кому? Чему? (kepada siapa? kepada apa?)",
        "Penerima tindakan (objek tak langsung), umur (мне 20 лет), rasa/keadaan (мне хо́лодно), " +
            "dan setelah preposisi к, по.",
        "Я звоню́ дру́гу.", "Saya menelepon teman.",
    ),
    CaseGuide(
        "Вин.", "Винительный падеж", "Akusatif", "Кого? Что? (siapa? apa? — sebagai objek)",
        "Objek langsung, dan arah/tujuan gerak setelah в, на. Untuk kata maskulin dan bentuk jamak, " +
            "makhluk bernyawa (одуш.) memakai bentuk genitif, benda tak bernyawa (неодуш.) memakai bentuk nominatif.",
        "Я ви́жу маши́ну. Я е́ду в шко́лу.", "Saya melihat mobil. Saya pergi ke sekolah.",
    ),
    CaseGuide(
        "Твор.", "Творительный падеж", "Instrumental", "Кем? Чем? (oleh siapa? dengan apa?)",
        "Alat/sarana (\"dengan …\"), kebersamaan setelah с, profesi setelah быть/стать/работать, " +
            "dan setelah preposisi над, под, за, перед, между.",
        "Я пишу́ ру́чкой. Он рабо́тает врачо́м.", "Saya menulis dengan pena. Dia bekerja sebagai dokter.",
    ),
    CaseGuide(
        "Пред.", "Предложный падеж", "Preposisional", "О ком? О чём? Где? (tentang siapa/apa? di mana?)",
        "Selalu dengan preposisi: в/на (lokasi: di …) dan о/об (tentang …).",
        "Я живу́ в Москве́. Мы говори́м о маши́не.", "Saya tinggal di Moskow. Kami berbicara tentang mobil.",
    ),
)

private val ABBREVIATIONS = listOf(
    Triple("Ед. ч.", "Единственное число", "bentuk tunggal"),
    Triple("Мн. ч.", "Множественное число", "bentuk jamak"),
    Triple("Муж. / Жен. / Ср.", "Мужской / женский / средний род", "gender maskulin / feminin / netral"),
    Triple("одуш. / неодуш.", "Одушевлённое / неодушевлённое", "bernyawa / tak bernyawa (memengaruhi bentuk akusatif)"),
    Triple("НСВ", "Несовершенный вид", "aspek imperfektif: proses, kebiasaan, tindakan berulang (делать)"),
    Triple("СВ", "Совершенный вид", "aspek perfektif: tindakan tuntas, hasil, satu kali (сделать)"),
    Triple("наст.", "Настоящее время", "kala kini — hanya untuk kata kerja НСВ"),
    Triple("прош.", "Прошедшее время", "kala lampau — berubah menurut gender dan jumlah, bukan persona"),
    Triple("будущ.", "Будущее время", "kala depan — СВ: bentuk sederhana (закажу́); НСВ: буду + infinitif"),
    Triple("повелит.", "Повелительное наклонение", "imperatif/perintah: ты (akrab) dan вы (sopan/jamak)"),
    Triple("Пр. действ.", "Действительное причастие", "partisip aktif: \"yang (sedang/telah) melakukan\" — чита́ющий, чита́вший"),
    Triple("Пр. страд.", "Страдательное причастие", "partisip pasif: \"yang di-…\" — чита́емый, прочи́танный"),
    Triple("Деепр.", "Деепричастие", "gerund/partisip adverbial: \"sambil …\" (чита́я) atau \"setelah …\" (прочита́в)"),
    Triple("Кратк. ф.", "Краткая форма", "bentuk pendek kata sifat, dipakai sebagai predikat (он гото́в)"),
    Triple("Сравн. ст.", "Сравнительная степень", "tingkat perbandingan: \"lebih …\" (нове́е)"),
    Triple("Превосх. ст.", "Превосходная степень", "tingkat superlatif: \"paling …\" (нове́йший)"),
    Triple("Местн. / Разд. / Зват.", "Местный / разделительный / звательный", "kasus tambahan pada sebagian kata: lokatif (в лесу́), partitif (ча́ю), vokatif"),
    Triple("*форма", "Редкая форма", "bentuk yang jarang, usang, atau janggal dipakai — ditampilkan miring berwarna merah tua"),
)

private val TIPS = listOf(
    "Ketik kata dalam bentuk apa pun: машинами, заказала, ели. Aplikasi mencari kata dasarnya dan menyorot posisi bentuk itu di tabel.",
    "Tanda tekan tidak perlu diketik, dan huruf ё boleh ditulis е (елка = ёлка).",
    "Bisa juga mencari dari bahasa Indonesia: mobil, makan, memesan.",
    "Satu ejaan bisa milik beberapa kata (homonim). Contoh: «есть» = makan, sekaligus bentuk kala kini dari быть. Semua tabelnya ditampilkan berurutan.",
    "Ketuk bentuk kata mana pun di tabel untuk mendengar pelafalannya dan melihat keterangan gramatikalnya.",
    "Ketuk ☆ untuk menyimpan kata. Kata tersimpan dan riwayat muncul saat kolom pencarian kosong.",
)

/** Tab ПОМОЩЬ: panduan kasus, singkatan tabel, dan cara pakai — dalam bahasa Indonesia. */
@Composable
fun HelpScreen() {
    LazyColumn(Modifier.fillMaxSize().testTag("help-screen"), contentPadding = PaddingValues(16.dp)) {
        item { Heading("Cara memakai aplikasi") }
        items(TIPS) { tip ->
            Row(Modifier.padding(bottom = 8.dp)) {
                Text("•", fontSize = 15.sp, color = AppColors.Teal, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(tip, fontSize = 15.sp, color = AppColors.TextDark, lineHeight = 21.sp)
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Heading("6 kasus bahasa Rusia (падежи)")
            Text(
                "Kata benda, kata sifat, pronomina, dan numeralia Rusia berubah akhirannya menurut fungsinya " +
                    "di dalam kalimat. Fungsi itu disebut kasus (падеж).",
                Modifier.padding(bottom = 10.dp), fontSize = 15.sp, color = AppColors.TextGray, lineHeight = 21.sp,
            )
        }
        items(CASES) { case ->
            Column(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Surface).padding(12.dp),
            ) {
                Row {
                    Text(case.abbr, Modifier.width(64.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AppColors.Teal)
                    Column {
                        Text("${case.name} · ${case.indonesian}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextDark)
                        Text(case.questions, fontSize = 14.sp, color = AppColors.TextGray)
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(case.usage, fontSize = 15.sp, color = AppColors.TextDark, lineHeight = 21.sp)
                Spacer(Modifier.height(6.dp))
                Text(case.example, fontSize = 16.sp, color = AppColors.TextDark, fontWeight = FontWeight.Medium)
                Text(case.translation, fontSize = 14.sp, color = AppColors.TextGray, fontStyle = FontStyle.Italic)
            }
        }
        item {
            Spacer(Modifier.height(14.dp))
            Heading("Arti singkatan di tabel")
        }
        items(ABBREVIATIONS) { (abbr, russian, indonesian) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                Row {
                    Text(abbr, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AppColors.TextDark)
                    Spacer(Modifier.width(8.dp))
                    Text(russian, fontSize = 14.sp, color = AppColors.TextMuted)
                }
                Text(indonesian, fontSize = 15.sp, color = AppColors.TextGray, lineHeight = 20.sp)
            }
            HorizontalDivider(color = AppColors.Divider)
        }
        item {
            Spacer(Modifier.height(14.dp))
            Heading("Persona pada tabel kata kerja")
            Text(
                "Я = saya · Ты = kamu · Он / Она / Оно = dia (lk. / pr. / netral) · Мы = kami/kita · " +
                    "Вы = Anda/kalian · Они = mereka.\n\n" +
                    "Kala lampau (прош.) tidak berubah menurut persona, melainkan menurut gender subjeknya: " +
                    "он заказа́л, она́ заказа́ла, оно́ заказа́ло, они́ заказа́ли.",
                fontSize = 15.sp, color = AppColors.TextDark, lineHeight = 21.sp,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, Modifier.padding(bottom = 8.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AppColors.TextDark)
}
