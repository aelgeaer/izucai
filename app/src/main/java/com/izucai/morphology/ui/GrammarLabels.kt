package com.izucai.morphology.ui

/** Label tata bahasa: singkatan Rusia seperti di tabel + penjelasan bahasa Indonesia. */
object GrammarLabels {
    data class Info(val abbr: String, val russian: String, val indonesian: String)

    val cases: Map<String, Info> = linkedMapOf(
        "nom" to Info("Им.", "Именительный", "nominatif"),
        "gen" to Info("Род.", "Родительный", "genitif"),
        "dat" to Info("Дат.", "Дательный", "datif"),
        "acc" to Info("Вин.", "Винительный", "akusatif"),
        "acc_inan" to Info("Вин. неодуш.", "Винительный (неодушевлённый)", "akusatif, benda tak bernyawa"),
        "acc_anim" to Info("Вин. одуш.", "Винительный (одушевлённый)", "akusatif, makhluk bernyawa"),
        "ins" to Info("Твор.", "Творительный", "instrumental"),
        "prep" to Info("Пред.", "Предложный", "preposisional"),
        "loc" to Info("Местн.", "Местный", "lokatif"),
        "par" to Info("Разд.", "Разделительный", "partitif"),
        "voc" to Info("Зват.", "Звательный", "vokatif"),
    )

    val numbers = mapOf(
        "sg" to Info("Ед. ч.", "Единственное число", "tunggal"),
        "pl" to Info("Мн. ч.", "Множественное число", "jamak"),
    )

    val genders = mapOf(
        "m" to Info("Муж.", "Мужской род", "maskulin"),
        "f" to Info("Жен.", "Женский род", "feminin"),
        "n" to Info("Ср.", "Средний род", "netral"),
        "pl" to Info("Мн. ч.", "Множественное число", "jamak"),
    )

    val persons = mapOf(
        "sg1" to Info("Я", "1-е лицо, ед. ч.", "orang ke-1 tunggal"),
        "sg2" to Info("Ты", "2-е лицо, ед. ч.", "orang ke-2 tunggal"),
        "sg3" to Info("Он\nОна\nОно", "3-е лицо, ед. ч.", "orang ke-3 tunggal"),
        "pl1" to Info("Мы", "1-е лицо, мн. ч.", "orang ke-1 jamak"),
        "pl2" to Info("Вы", "2-е лицо, мн. ч.", "orang ke-2 jamak"),
        "pl3" to Info("Они", "3-е лицо, мн. ч.", "orang ke-3 jamak"),
    )
    val personOrder = listOf("sg1", "sg2", "sg3", "pl1", "pl2", "pl3")

    val tenses = mapOf(
        "pres" to Info("наст.", "Настоящее время", "kala kini"),
        "fut" to Info("будущ.", "Будущее время", "kala depan"),
        "past" to Info("прош.", "Прошедшее время", "kala lampau"),
        "imp" to Info("повелит.", "Повелительное наклонение", "imperatif (perintah)"),
    )

    val participles: Map<String, Info> = linkedMapOf(
        "pres_act" to Info("Пр. действ. наст.", "Причастие действительное, наст. вр.", "partisip aktif kala kini"),
        "past_act" to Info("Пр. действ. прош.", "Причастие действительное, прош. вр.", "partisip aktif kala lampau"),
        "pres_adv" to Info("Деепр. наст.", "Деепричастие наст. вр.", "gerund (partisip adverbial) kala kini"),
        "past_adv" to Info("Деепр. прош.", "Деепричастие прош. вр.", "gerund (partisip adverbial) kala lampau"),
        "pres_pass" to Info("Пр. страд. наст.", "Причастие страдательное, наст. вр.", "partisip pasif kala kini"),
        "past_pass" to Info("Пр. страд. прош.", "Причастие страдательное, прош. вр.", "partisip pasif kala lampau"),
    )

    fun posLabel(pos: String): String = when (pos) {
        "NOUN" -> "сущ. · kata benda"
        "VERB" -> "глаг. · kata kerja"
        "ADJ" -> "прил. · kata sifat"
        "PRON" -> "мест. · pronomina"
        "NUM" -> "числ. · numeralia"
        else -> pos
    }

    fun genderIndonesian(gender: String): String = gender
        .replace("муж.", "муж. (maskulin)")
        .replace("жен.", "жен. (feminin)")
        .replace("ср.", "ср. (netral)")

    fun aspectIndonesian(aspect: String): String = when (aspect) {
        "impf" -> "aspek imperfektif: proses, kebiasaan, atau tindakan berulang"
        "pf" -> "aspek perfektif: tindakan tuntas / satu kali selesai"
        else -> "dua aspek: bentuk yang sama dipakai untuk imperfektif dan perfektif"
    }
}
