"""Paradigma yang ditulis tangan untuk kelas kata tertutup & kata tak beraturan.

- быть      : tabel lengkap (Прошедшее / Настоящее / Будущее / Повелит. накл. + partisip per gender)
- pronomina : я, ты, он, ... мой, тот, весь, ... (tabel Wiktionary untuk kata-kata ini digabung
              menjadi satu tabel besar sehingga tidak bisa diurai otomatis dengan andal)
- bentuk jarang (ditandai "*") untuk kata kerja tertentu

Notasi: apostrof setelah vokal = tanda tekan (меня' -> меня́); "/" memisahkan varian;
"|" memisahkan kasus dengan urutan Им.|Род.|Дат.|Вин.|Твор.|Пред.
"""
from __future__ import annotations

from kaikki_parser import (
    ASPECT_LABELS, CASE_LABELS, GENDER_LABELS, MAIN_CASES, PARTICIPLE_KEYS, PARTICIPLE_LABELS,
    PERSON_KEYS, PERSON_LABELS, Parsed, make_simple,
)

ACUTE = "́"
ADJ_ROWS = ["nom", "gen", "dat", "acc_inan", "acc_anim", "ins", "prep"]
COLS = ("m", "f", "n", "pl")


def s(text: str) -> str:
    return text.replace("'", ACUTE)


def cell(text: str) -> list[str]:
    return [s(v.strip()) for v in text.split("/") if v.strip() and v.strip() != "-"]


# ---------------------------------------------------------------- pronomina sederhana

SIMPLE_PRONOUNS: dict[str, tuple[str, str]] = {
    # kunci: (lemma bertanda tekan, "Им.|Род.|Дат.|Вин.|Твор.|Пред.")
    "я": ("я", "я|меня'|мне|меня'|мной/мно'ю|мне"),
    "ты": ("ты", "ты|тебя'|тебе'|тебя'|тобо'й/тобо'ю|тебе'"),
    "он": ("он", "он|его'/него'|ему'/нему'|его'/него'|им/ним|нём"),
    "она": ("она'", "она'|её/неё|ей/ней|её/неё|ей/ней/е'ю/не'ю|ней"),
    "оно": ("оно'", "оно'|его'/него'|ему'/нему'|его'/него'|им/ним|нём"),
    "мы": ("мы", "мы|нас|нам|нас|на'ми|нас"),
    "вы": ("вы", "вы|вас|вам|вас|ва'ми|вас"),
    "они": ("они'", "они'|их/них|им/ним|их/них|и'ми/ни'ми|них"),
    "себя": ("себя'", "-|себя'|себе'|себя'|собо'й/собо'ю|себе'"),
    "кто": ("кто", "кто|кого'|кому'|кого'|кем|ком"),
    "что": ("что", "что|чего'|чему'|что|чем|чём"),
    "никто": ("никто'", "никто'|никого'|никому'|никого'|нике'м|ни о ко'м"),
    "ничто": ("ничто'", "ничто'|ничего'|ничему'|ничто'|ниче'м|ни о чём"),
    "некто": ("не'кто", "не'кто|-|-|-|-|-"),
    "нечто": ("не'что", "не'что|-|-|не'что|-|-"),
    "некого": ("не'кого", "-|не'кого|не'кому|не'кого|не'кем|не' о ком"),
    "нечего": ("не'чего", "-|не'чего|не'чему|не'чего|не'чем|не' о чем"),
    "кто-то": ("кто'-то", "кто'-то|кого'-то|кому'-то|кого'-то|ке'м-то|ко'м-то"),
    "что-то": ("что'-то", "что'-то|чего'-то|чему'-то|что'-то|че'м-то|чём-то"),
    "кто-нибудь": ("кто'-нибудь", "кто'-нибудь|кого'-нибудь|кому'-нибудь|кого'-нибудь|ке'м-нибудь|ко'м-нибудь"),
    "что-нибудь": ("что'-нибудь", "что'-нибудь|чего'-нибудь|чему'-нибудь|что'-нибудь|че'м-нибудь|чём-нибудь"),
    "кое-кто": ("кое-кто'", "кое-кто'|кое-кого'|кое-кому'|кое-кого'|кое-ке'м|кое о ко'м"),
    "кое-что": ("кое-что'", "кое-что'|кое-чего'|кое-чему'|кое-что'|кое-че'м|кое о чём"),
    "сколько": ("ско'лько", "ско'лько|ско'льких|ско'льким|ско'лько/ско'льких|ско'лькими|ско'льких"),
    "столько": ("сто'лько", "сто'лько|сто'льких|сто'льким|сто'лько/сто'льких|сто'лькими|сто'льких"),
    "несколько": ("не'сколько", "не'сколько|не'скольких|не'скольким|не'сколько/не'скольких|не'сколькими|не'скольких"),
    "много": ("мно'го", "мно'го|мно'гих|мно'гим|мно'го/мно'гих|мно'гими|мно'гих"),
}

# ----------------------------------------------------------------- pronomina bergender
# Tiap kolom: "Им.|Род.|Дат.|Вин. неодуш.|Вин. одуш.|Твор.|Пред."

_MOY = {
    "m": "мой|моего'|моему'|мой|моего'|мои'м|моём",
    "f": "моя'|мое'й|мое'й|мою'|мою'|мое'й/мое'ю|мое'й",
    "n": "моё|моего'|моему'|моё|моё|мои'м|моём",
    "pl": "мои'|мои'х|мои'м|мои'|мои'х|мои'ми|мои'х",
}
_NASH = {
    "m": "наш|на'шего|на'шему|наш|на'шего|на'шим|на'шем",
    "f": "на'ша|на'шей|на'шей|на'шу|на'шу|на'шей/на'шею|на'шей",
    "n": "на'ше|на'шего|на'шему|на'ше|на'ше|на'шим|на'шем",
    "pl": "на'ши|на'ших|на'шим|на'ши|на'ших|на'шими|на'ших",
}
_CHEY = {
    "m": "чей|чьего'|чьему'|чей|чьего'|чьим|чьём",
    "f": "чья|чьей|чьей|чью|чью|чьей/чье'ю|чьей",
    "n": "чьё|чьего'|чьему'|чьё|чьё|чьим|чьём",
    "pl": "чьи|чьих|чьим|чьи|чьих|чьи'ми|чьих",
}


_TAKOY = {
    "m": "тако'й|тако'го|тако'му|тако'й|тако'го|таки'м|тако'м",
    "f": "така'я|тако'й|тако'й|таку'ю|таку'ю|тако'й/тако'ю|тако'й",
    "n": "тако'е|тако'го|тако'му|тако'е|тако'е|таки'м|тако'м",
    "pl": "таки'е|таки'х|таки'м|таки'е|таки'х|таки'ми|таки'х",
}


def _reprefix(table: dict[str, str], old: str, new: str) -> dict[str, str]:
    return {
        col: "|".join("/".join(new + v[len(old):] for v in c.split("/")) for c in spec.split("|"))
        for col, spec in table.items()
    }


def _suffix(table: dict[str, str], suffix: str) -> dict[str, str]:
    return {
        col: "|".join("/".join(v + suffix for v in c.split("/")) for c in spec.split("|"))
        for col, spec in table.items()
    }


GENDERED_PRONOUNS: dict[str, tuple[str, dict[str, str]]] = {
    "мой": ("мой", _MOY),
    "твой": ("твой", _reprefix(_MOY, "мо", "тво")),
    "свой": ("свой", _reprefix(_MOY, "мо", "сво")),
    "наш": ("наш", _NASH),
    "ваш": ("ваш", _reprefix(_NASH, "на", "ва")),
    "этот": ("э'тот", {
        "m": "э'тот|э'того|э'тому|э'тот|э'того|э'тим|э'том",
        "f": "э'та|э'той|э'той|э'ту|э'ту|э'той/э'тою|э'той",
        "n": "э'то|э'того|э'тому|э'то|э'то|э'тим|э'том",
        "pl": "э'ти|э'тих|э'тим|э'ти|э'тих|э'тими|э'тих",
    }),
    "тот": ("тот", {
        "m": "тот|того'|тому'|тот|того'|тем|том",
        "f": "та|той|той|ту|ту|той/то'ю|той",
        "n": "то|того'|тому'|то|то|тем|том",
        "pl": "те|тех|тем|те|тех|те'ми|тех",
    }),
    "весь": ("весь", {
        "m": "весь|всего'|всему'|весь|всего'|всем|всём",
        "f": "вся|всей|всей|всю|всю|всей/все'ю|всей",
        "n": "всё|всего'|всему'|всё|всё|всем|всём",
        "pl": "все|всех|всем|все|всех|все'ми|всех",
    }),
    "сам": ("сам", {
        "m": "сам|самого'|самому'|сам|самого'|сами'м|само'м",
        "f": "сама'|само'й|само'й|саму'/самоё|саму'/самоё|само'й/само'ю|само'й",
        "n": "само'|самого'|самому'|само'|само'|сами'м|само'м",
        "pl": "са'ми|сами'х|сами'м|са'ми|сами'х|сами'ми|сами'х",
    }),
    "такой": ("тако'й", _TAKOY),
    "какой": ("како'й", _reprefix(_TAKOY, "так", "как")),
    "таков": ("тако'в", {"m": "тако'в|-|-|-|-|-|-", "f": "такова'|-|-|-|-|-|-", "n": "таково'|-|-|-|-|-|-", "pl": "таковы'|-|-|-|-|-|-"}),
    "каков": ("како'в", {"m": "како'в|-|-|-|-|-|-", "f": "какова'|-|-|-|-|-|-", "n": "каково'|-|-|-|-|-|-", "pl": "каковы'|-|-|-|-|-|-"}),
    "многие": ("мно'гие", {"pl": "мно'гие|мно'гих|мно'гим|мно'гие|мно'гих|мно'гими|мно'гих"}),
    "чей": ("чей", _CHEY),
    "чей-то": ("чей-то", _suffix(_CHEY, "-то")),
    "ничей": ("ниче'й", {
        "m": "ниче'й|ничьего'|ничьему'|ниче'й|ничьего'|ничьи'м|ничьём",
        "f": "ничья'|ничье'й|ничье'й|ничью'|ничью'|ничье'й/ничье'ю|ничье'й",
        "n": "ничьё|ничьего'|ничьему'|ничьё|ничьё|ничьи'м|ничьём",
        "pl": "ничьи'|ничьи'х|ничьи'м|ничьи'|ничьи'х|ничьи'ми|ничьи'х",
    }),
}

# Numeralia bergender tanpa kolom jamak: (lemma, kolom m/n, kolom f)
GENDERED_NUMERALS: dict[str, tuple[str, dict[str, str]]] = {
    "два": ("два", {
        "m": "два|двух|двум|два|двух|двумя'|двух",
        "f": "две|двух|двум|две|двух|двумя'|двух",
        "n": "два|двух|двум|два|двух|двумя'|двух",
    }),
    "оба": ("о'ба", {
        "m": "о'ба|обо'их|обо'им|о'ба|обо'их|обо'ими|обо'их",
        "f": "о'бе|обе'их|обе'им|о'бе|обе'их|обе'ими|обе'их",
        "n": "о'ба|обо'их|обо'им|о'ба|обо'их|обо'ими|обо'их",
    }),
    "полтора": ("полтора'", {
        "m": "полтора'|полу'тора|полу'тора|полтора'|полтора'|полу'тора|полу'тора",
        "f": "полторы'|полу'тора|полу'тора|полторы'|полторы'|полу'тора|полу'тора",
        "n": "полтора'|полу'тора|полу'тора|полтора'|полтора'|полу'тора|полу'тора",
    }),
}


def simple_pronoun(key: str) -> tuple[str, Parsed]:
    lemma, spec = SIMPLE_PRONOUNS[key]
    table = {case: cell(part) for case, part in zip(MAIN_CASES, spec.split("|"))}
    return s(lemma), make_simple(table)


def gendered(key: str, source: dict[str, tuple[str, dict[str, str]]]) -> tuple[str, Parsed]:
    lemma, columns = source[key]
    rows = []
    cells: list[tuple[str, str, str]] = []
    split = {col: spec.split("|") for col, spec in columns.items()}
    for i, case in enumerate(ADJ_ROWS):
        row: dict = {"case": case}
        for col in COLS:
            forms = cell(split[col][i]) if col in split else []
            row[col] = forms
            cells += [(f"{case}.{col}", f, f"{CASE_LABELS[case]}, {GENDER_LABELS[col]}") for f in forms]
        if any(row[col] for col in COLS):
            rows.append(row)
    return s(lemma), Parsed("adj", {"kind": "adj", "rows": rows}, cells)


def all_special_pronouns() -> dict[tuple[str, str], tuple[str, Parsed]]:
    """{(lemma polos, POS): (lemma bertanda tekan, paradigma)}"""
    out: dict[tuple[str, str], tuple[str, Parsed]] = {}
    for key in SIMPLE_PRONOUNS:
        out[(key, "PRON")] = simple_pronoun(key)
    for key in GENDERED_PRONOUNS:
        out[(key, "PRON")] = gendered(key, GENDERED_PRONOUNS)
    for key in GENDERED_NUMERALS:
        out[(key, "NUM")] = gendered(key, GENDERED_NUMERALS)
    return out


# ----------------------------------------------------------------------------- быть

def byt() -> tuple[str, Parsed]:
    yest = cell("е'сть")
    pres = {p: list(yest) for p in PERSON_KEYS}
    fut = dict(zip(PERSON_KEYS, map(cell, ["бу'ду", "бу'дешь", "бу'дет", "бу'дем", "бу'дете", "бу'дут"])))
    past = {"m": cell("бы'л"), "f": cell("была'"), "n": cell("бы'ло"), "pl": cell("бы'ли")}
    imp = {"sg": cell("бу'дь"), "pl": cell("бу'дьте")}
    part = {k: [] for k in PARTICIPLE_KEYS}
    part["pres_act"] = cell("су'щий")
    part["past_act"] = cell("бы'вший")
    part["pres_adv"] = cell("бу'дучи")
    part["past_adv"] = cell("бы'в/бы'вши")

    past_sg = cell("бы'л/была'/бы'ло")
    full_table = {
        "persons": [
            {"label": "I лицо", "past_sg": past_sg, "past_pl": cell("бы'ли"),
             "pres_sg": [s("е'сть"), s("(е'смь¹)")], "pres_pl": [s("е'сть"), s("(есмы'²)")],
             "fut_sg": cell("бу'ду"), "fut_pl": cell("бу'дем"), "imp_sg": [], "imp_pl": cell("бу'дем")},
            {"label": "II лицо", "past_sg": past_sg, "past_pl": cell("бы'ли"),
             "pres_sg": [s("е'сть"), s("(еси'³)")], "pres_pl": [s("е'сть"), s("(е'сте⁴)")],
             "fut_sg": cell("бу'дешь"), "fut_pl": cell("бу'дете"), "imp_sg": cell("бу'дь"), "imp_pl": cell("бу'дьте")},
            {"label": "III лицо", "past_sg": past_sg, "past_pl": cell("бы'ли"),
             "pres_sg": [s("е'сть⁵")], "pres_pl": [s("е'сть"), s("(су'ть⁶)")],
             "fut_sg": cell("бу'дет"), "fut_pl": cell("бу'дут"), "imp_sg": [], "imp_pl": []},
        ],
        "participles": [
            {"label": "Прич. действ. наст. времени", "m": [s("су'щий⁷")], "f": [s("су'щая⁷")],
             "n": [s("су'щее⁷")], "pl": [s("су'щие⁷")]},
            {"label": "Прич. действ. прош. времени", "m": cell("бы'вший"), "f": cell("бы'вшая"),
             "n": cell("бы'вшее"), "pl": cell("бы'вшие")},
            {"label": "Прич. буд. времени", "m": cell("бу'дущий"), "f": cell("бу'дущая"),
             "n": cell("бу'дущее"), "pl": cell("бу'дущие")},
            {"label": "Деепр. наст. времени", "all": cell("бу'дучи")},
            {"label": "Деепр. прош. времени", "all": cell("бы'в/бы'вши")},
            {"label": "Частица сослаг. накл.", "all": [s("бы"), s("б"), s("бу'де⁸")]},
            {"label": "Арх. повелит.", "all": [s("бу'ди⁹")]},
        ],
        "footnotes": [
            "¹ есмь — bentuk arkais orang ke-1 tunggal; kini hanya dalam gaya kitab suci atau gurauan.",
            "² есмы — bentuk arkais orang ke-1 jamak.",
            "³ еси — bentuk arkais orang ke-2 tunggal (dikenal dari dongeng dan doa).",
            "⁴ есте — bentuk arkais orang ke-2 jamak.",
            "⁵ есть — dalam bahasa Rusia modern dipakai untuk semua persona dan biasanya dihilangkan: «Он студент».",
            "⁶ суть — bentuk orang ke-3 jamak; masih dipakai dalam gaya ilmiah/buku.",
            "⁷ сущий — kini berfungsi sebagai kata sifat (\"sejati, benar-benar\"), jarang sebagai partisip.",
            "⁸ буде — kata sambung usang yang berarti \"jikalau\".",
            "⁹ буди — bentuk imperatif arkais (gaya gereja).",
        ],
    }

    paradigm = {
        "kind": "verb", "aspect": "impf", "infinitive": cell("бы'ть"),
        "present": pres, "future": fut, "past": past, "imperative": imp, "participles": part,
        "full_table": full_table,
    }

    cells: list[tuple[str, str, str]] = [("inf", s("бы'ть"), "Инфинитив")]
    cells.append(("pres.sg3", s("е'сть"), "Наст., все лица"))
    for p in PERSON_KEYS:
        cells += [(f"fut.{p}", f, f"Будущ., {PERSON_LABELS[p]}") for f in fut[p]]
    for g in ("m", "f", "n"):
        cells += [(f"past.{g}", f, f"Прош., {GENDER_LABELS[g]}, ед. ч.") for f in past[g]]
    cells += [("past.pl", f, "Прош., мн. ч.") for f in past["pl"]]
    cells += [("imp.sg", s("бу'дь"), "Повелит., ед. ч."), ("imp.pl", s("бу'дьте"), "Повелит., мн. ч.")]
    for k in PARTICIPLE_KEYS:
        cells += [(f"part.{k}", f, PARTICIPLE_LABELS[k]) for f in part[k]]
    # Bentuk khusus dari tabel lengkap (arkais + partisip per gender)
    archaic = [
        ("е'смь", "Наст., 1 л., ед. ч. (арх.)"), ("еси'", "Наст., 2 л., ед. ч. (арх.)"),
        ("есмы'", "Наст., 1 л., мн. ч. (арх.)"), ("е'сте", "Наст., 2 л., мн. ч. (арх.)"),
        ("су'ть", "Наст., 3 л., мн. ч. (книжн.)"), ("бу'ди", "Повелит., ед. ч. (арх.)"),
    ]
    cells += [("full", s(f), info) for f, info in archaic]
    for stem, label in (("су'щ", "Пр. действ. наст."), ("бы'вш", "Пр. действ. прош."), ("бу'дущ", "Прич. буд. времени")):
        for ending, g in (("ая", "жен."), ("ее", "ср."), ("ие", "мн. ч.")):
            cells.append(("full", s(stem + ending), f"{label}, {g}"))
    cells.append(("full", s("бу'дущий"), "Прич. буд. времени, муж."))
    return s("бы'ть"), Parsed("verb", paradigm, cells, aspect=ASPECT_LABELS["impf"])


# ---------------------------------------------------------------------- bentuk jarang
# Bentuk yang secara teoretis ada tetapi jarang/janggal dipakai; ditampilkan dengan "*".
# {lemma polos: {kunci partisip: [bentuk tanpa tanda tekan]}}
RARE_FORMS: dict[str, dict[str, list[str]]] = {
    "есть": {"pres_adv": ["едя"], "pres_pass": ["едомый"]},
}

# ------------------------------------------------------------ koreksi komparatif
# Wiktionary membentuk komparatif "+" secara otomatis dari akar kata sehingga tekanannya
# keliru untuk beberapa kata sifat yang bentuk pendek femininnya bertekanan di akhiran
# (нова́ -> нове́е). {lemma polos: bentuk yang benar}
COMPARATIVE_OVERRIDES: dict[str, list[str]] = {
    "новый": [s("нове'е"), s("нове'й")],
    "шустрый": [s("шустре'е"), s("шустре'й")],
}
