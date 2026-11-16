"""Ubah entri Kaikki/Wiktionary menjadi struktur paradigma (paradigm_json) aplikasi.

Empat jenis tata letak (`kind`):
  noun   : baris kasus x (sg, pl)
  adj    : baris kasus x (m, f, n, pl) + bentuk pendek + komparatif
  verb   : konjugasi, lampau, imperatif, partisip & gerund
  simple : baris kasus x satu kolom (pronomina persona, numeralia tak berjenis)

Setiap sel adalah daftar varian (list[str]) lengkap dengan tanda tekan U+0301.
Awalan "*" pada sebuah varian menandai bentuk yang jarang/janggal.
"""
from __future__ import annotations

import re
from dataclasses import dataclass, field

from textnorm import clean_form, ensure_stress, is_pre_reform, lemma_clean

CASE_KEYS = {
    "nominative": "nom",
    "genitive": "gen",
    "dative": "dat",
    "accusative": "acc",
    "instrumental": "ins",
    "prepositional": "prep",
    "locative": "loc",
    "partitive": "par",
    "vocative": "voc",
}
MAIN_CASES = ["nom", "gen", "dat", "acc", "ins", "prep"]
EXTRA_CASES = ["loc", "par", "voc"]
CASE_LABELS = {
    "nom": "Им.", "gen": "Род.", "dat": "Дат.", "acc": "Вин.", "ins": "Твор.", "prep": "Пред.",
    "acc_inan": "Вин. (неодуш.)", "acc_anim": "Вин. (одуш.)",
    "loc": "Местн.", "par": "Разд.", "voc": "Зват.",
}
NUMBER_LABELS = {"sg": "Ед. ч.", "pl": "Мн. ч."}
GENDER_LABELS = {"m": "муж.", "f": "жен.", "n": "ср.", "pl": "мн. ч."}
PERSON_KEYS = ["sg1", "sg2", "sg3", "pl1", "pl2", "pl3"]
PERSON_LABELS = {
    "sg1": "1 л., ед. ч.", "sg2": "2 л., ед. ч.", "sg3": "3 л., ед. ч.",
    "pl1": "1 л., мн. ч.", "pl2": "2 л., мн. ч.", "pl3": "3 л., мн. ч.",
}
PARTICIPLE_KEYS = ["pres_act", "past_act", "pres_adv", "past_adv", "pres_pass", "past_pass"]
PARTICIPLE_LABELS = {
    "pres_act": "Пр. действ. наст.", "past_act": "Пр. действ. прош.",
    "pres_adv": "Деепр. наст.", "past_adv": "Деепр. прош.",
    "pres_pass": "Пр. страд. наст.", "past_pass": "Пр. страд. прош.",
}
ASPECT_LABELS = {
    "impf": "Несовершенный вид (НСВ)",
    "pf": "Совершенный вид (СВ)",
    "both": "Двувидовой глагол (НСВ и СВ)",
}

_META_TAGS = {"table-tags", "inflection-template", "class", "romanization", "error-unrecognized-form"}
# Varian yang ada di tabel Wiktionary tetapi jarang/usang/tidak baku -> ditandai "*".
_RARE_TAGS = {"rare", "uncommon", "obsolete", "poetic", "archaic", "proscribed", "nonstandard", "dialectal"}
RARE_NOTE = " (редк.)"
_LATIN_RE = re.compile(r"[A-Za-zÀ-ɏʹʺ]")
_CYRILLIC_RE = re.compile(r"[а-яёА-ЯЁ]")
_SPLIT_RE = re.compile(r"\s*[,/]\s*|\s+or\s+")
_PAREN_PREFIX_RE = re.compile(r"\([^)]*\)")


@dataclass
class Parsed:
    kind: str
    paradigm: dict
    # (cell_key, bentuk bertanda tekan, gram_info)
    cells: list[tuple[str, str, str]] = field(default_factory=list)
    gender: str | None = None
    aspect: str | None = None


def _add(cell: list[str], form: str) -> None:
    if form and form not in cell:
        cell.append(form)


def tidy(cell: list[str]) -> list[str]:
    """Varian baku lebih dulu; buang tanda "*" bila bentuk yang sama juga tercatat sebagai baku."""
    plain = [f for f in cell if not f.startswith("*")]
    rare = [f for f in cell if f.startswith("*") and f[1:] not in plain]
    return plain + rare


def emit(cells: list[tuple[str, str, str]], key: str, forms: list[str], label: str) -> None:
    """Catat bentuk-bentuk sebuah sel untuk tabel word_forms (tanpa tanda "*")."""
    for f in forms:
        if f.startswith("*"):
            cells.append((key, f[1:], label + RARE_NOTE))
        else:
            cells.append((key, f, label))


def split_forms(raw: str, *, stress_monosyllables: bool) -> list[str]:
    """Pecah satu string bentuk menjadi varian yang bersih; buang yang tidak layak."""
    out: list[str] = []
    for part in _SPLIT_RE.split(raw):
        part = clean_form(part)
        if not part or part in {"-", "—", "–"}:
            continue
        if _LATIN_RE.search(part) or not _CYRILLIC_RE.search(part):
            continue
        if is_pre_reform(part):
            continue
        if stress_monosyllables:
            part = ensure_stress(part)
        _add(out, part)
    return out


def table_forms(entry: dict, source: str, *, stress_monosyllables: bool):
    """Hasilkan (bentuk, himpunan tag) dari tabel infleksi sebuah entri."""
    for f in entry.get("forms", ()):
        if f.get("source") != source:
            continue
        tags = set(f.get("tags") or ())
        if tags & _META_TAGS or "dated" in tags:
            continue
        rare = bool(tags & _RARE_TAGS)
        raw = f.get("form") or ""
        if " " in raw.strip() and source == "conjugation":
            continue  # bentuk majemuk (бу́ду де́лать) dirakit di aplikasi
        for form in split_forms(raw, stress_monosyllables=stress_monosyllables):
            if " " in form:
                continue
            yield ("*" + form if rare else form), tags


def canonical_form(entry: dict) -> tuple[str, set[str]]:
    """Bentuk kamus bertanda tekan + tag kepala (gender, aspek, keanimasian)."""
    for f in entry.get("forms", ()):
        tags = set(f.get("tags") or ())
        if "canonical" in tags and not is_pre_reform(f.get("form", "")):
            return clean_form(f["form"]), tags
    return entry["word"], set()


def head_forms(entry: dict, tag: str) -> list[str]:
    """Bentuk di baris kepala entri (bukan dari tabel) dengan tag tertentu."""
    out: list[str] = []
    for f in entry.get("forms", ()):
        if f.get("source"):
            continue
        tags = f.get("tags") or ()
        if tag in tags and "canonical" not in tags:
            raw = _PAREN_PREFIX_RE.sub("", f.get("form") or "")
            for form in split_forms(raw, stress_monosyllables=False):
                if " " not in form:
                    _add(out, form)
    return out


def _gender_label(tags: set[str]) -> str | None:
    parts = [lbl for key, lbl in (("masculine", "муж."), ("feminine", "жен."), ("neuter", "ср.")) if key in tags]
    return "/".join(parts) if parts else None


# --------------------------------------------------------------------------- NOUN

def parse_noun(entry: dict) -> Parsed | None:
    lemma, head_tags = canonical_form(entry)
    table: dict[str, dict[str, list[str]]] = {}
    for form, tags in table_forms(entry, "declension", stress_monosyllables=True):
        cases = [CASE_KEYS[t] for t in tags if t in CASE_KEYS]
        if len(cases) != 1:
            continue
        if "plural" in tags:
            num = "pl"
        elif "singular" in tags:
            num = "sg"
        else:
            continue
        _add(table.setdefault(cases[0], {"sg": [], "pl": []})[num], form)

    indeclinable = (
        "indeclinable" in head_tags
        or any("indeclinable" in (s.get("tags") or ()) for s in entry.get("senses", ()))
        or any("indeclinable" in (h.get("expansion") or "") for h in entry.get("head_templates") or ())
    )
    if not table:
        if not indeclinable:
            return None
        base = ensure_stress(lemma)
        table = {c: {"sg": [base], "pl": [base]} for c in MAIN_CASES}

    if not any(table.get(c) for c in MAIN_CASES):
        return None
    rows = []
    cells: list[tuple[str, str, str]] = []
    for case in MAIN_CASES + EXTRA_CASES:
        if case not in table and case in EXTRA_CASES:
            continue
        cell = table.get(case, {"sg": [], "pl": []})
        cell = {"sg": tidy(cell["sg"]), "pl": tidy(cell["pl"])}
        rows.append({"case": case, "sg": cell["sg"], "pl": cell["pl"]})
        for num in ("sg", "pl"):
            emit(cells, f"{case}.{num}", cell[num], f"{CASE_LABELS[case]}, {NUMBER_LABELS[num]}")

    has_sg = any(r["sg"] for r in rows)
    has_pl = any(r["pl"] for r in rows)
    if not has_sg and "plural" not in head_tags:
        return None  # tabel tanpa bentuk tunggal padahal bukan pluralia tantum -> bukan tabel kata benda ini
    paradigm = {"kind": "noun", "rows": rows}
    if "animate" in head_tags and "inanimate" not in head_tags:
        paradigm["animacy"] = "одуш."
    elif "inanimate" in head_tags and "animate" not in head_tags:
        paradigm["animacy"] = "неодуш."
    if indeclinable:
        paradigm["indeclinable"] = True
    if has_sg and not has_pl:
        paradigm["number"] = "sg_only"
    elif has_pl and not has_sg:
        paradigm["number"] = "pl_only"
    return Parsed("noun", paradigm, cells, gender=_gender_label(head_tags))


# ---------------------------------------------------------------------------- ADJ

def parse_adjective(entry: dict, *, stress_monosyllables: bool = True) -> Parsed | None:
    """Tabel bergender (kata sifat, serta pronomina/numeralia yang berdeklinasi seperti kata sifat)."""
    cols = ("m", "f", "n", "pl")
    table: dict[str, dict[str, list[str]]] = {}
    short: dict[str, list[str]] = {c: [] for c in cols}

    def targets(tags: set[str]) -> list[str]:
        t = [c for key, c in (("masculine", "m"), ("feminine", "f"), ("neuter", "n")) if key in tags]
        if "plural" in tags:
            t.append("pl")
        return t

    for form, tags in table_forms(entry, "declension", stress_monosyllables=stress_monosyllables):
        tg = targets(tags)
        if not tg:
            continue
        if "short-form" in tags:
            for c in tg:
                _add(short[c], form)
            continue
        cases = [CASE_KEYS[t] for t in tags if t in CASE_KEYS]
        if len(cases) != 1 or cases[0] not in MAIN_CASES:
            continue
        case = cases[0]
        if case == "acc":
            if "animate" in tags and "inanimate" not in tags:
                keys = ["acc_anim"]
            elif "inanimate" in tags and "animate" not in tags:
                keys = ["acc_inan"]
            else:
                keys = ["acc_inan", "acc_anim"]
        else:
            keys = [case]
        for key in keys:
            row = table.setdefault(key, {c: [] for c in cols})
            for c in tg:
                _add(row[c], form)

    if "nom" not in table or "gen" not in table or not table["nom"]["m"]:
        return None

    order = ["nom", "gen", "dat", "acc_inan", "acc_anim", "ins", "prep"]
    rows = []
    cells: list[tuple[str, str, str]] = []
    for key in order:
        row = table.get(key)
        if row is None:
            continue
        row = {c: tidy(row[c]) for c in cols}
        rows.append({"case": key, **row})
        for c in cols:
            emit(cells, f"{key}.{c}", row[c], f"{CASE_LABELS[key]}, {GENDER_LABELS[c]}")

    paradigm: dict = {"kind": "adj", "rows": rows}
    if any(short.values()):
        short = {c: tidy(short[c]) for c in cols}
        paradigm["short"] = short
        for c in cols:
            emit(cells, f"short.{c}", short[c], f"Кратк. ф., {GENDER_LABELS[c]}")
    comparative = head_forms(entry, "comparative")
    if comparative:
        paradigm["comparative"] = comparative
        cells += [("comp", f, "Сравн. ст.") for f in comparative]
    superlative = head_forms(entry, "superlative")
    if superlative:
        paradigm["superlative"] = superlative
        cells += [("sup", f, "Превосх. ст.") for f in superlative]
    return Parsed("adj", paradigm, cells)


# ------------------------------------------------------------------------- SIMPLE

def parse_simple(entry: dict) -> Parsed | None:
    """Tabel satu kolom: hanya kasus, tanpa bilangan/gender (пять, сорок, ...)."""
    table: dict[str, list[str]] = {}
    for form, tags in table_forms(entry, "declension", stress_monosyllables=False):
        cases = [CASE_KEYS[t] for t in tags if t in CASE_KEYS]
        if len(cases) != 1 or cases[0] not in MAIN_CASES:
            continue
        if tags & {"masculine", "feminine", "neuter", "personal", "first-person", "second-person", "third-person"}:
            return None  # tabel bergender / tabel gabungan pronomina -> bukan tata letak ini
        _add(table.setdefault(cases[0], []), form)
    if "nom" not in table or "gen" not in table:
        return None
    return make_simple(table)


def make_simple(table: dict[str, list[str]]) -> Parsed:
    rows = []
    cells: list[tuple[str, str, str]] = []
    for case in MAIN_CASES:
        forms = tidy(table.get(case, []))
        rows.append({"case": case, "forms": forms})
        emit(cells, case, forms, CASE_LABELS[case])
    return Parsed("simple", {"kind": "simple", "rows": rows}, cells)


# --------------------------------------------------------------------------- VERB

def parse_verb(entry: dict) -> Parsed | None:
    lemma, head_tags = canonical_form(entry)
    pres = {p: [] for p in PERSON_KEYS}
    fut = {p: [] for p in PERSON_KEYS}
    past = {g: [] for g in ("m", "f", "n", "pl")}
    imp = {"sg": [], "pl": []}
    part = {k: [] for k in PARTICIPLE_KEYS}
    inf: list[str] = []
    table_aspects: set[str] = set()

    for form, tags in table_forms(entry, "conjugation", stress_monosyllables=True):
        if "infinitive" in tags:
            _add(inf, form)
            table_aspects |= tags & {"imperfective", "perfective"}
            continue
        if "participle" in tags:
            tense = "pres" if "present" in tags else "past" if "past" in tags else None
            voice = "adv" if "adverbial" in tags else "act" if "active" in tags else "pass" if "passive" in tags else None
            if tense and voice:
                _add(part[f"{tense}_{voice}"], form)
            continue
        if "imperative" in tags:
            _add(imp["pl" if "plural" in tags else "sg"], form)
            continue
        if "past" in tags:
            if "plural" in tags:
                _add(past["pl"], form)
            else:
                for key, g in (("masculine", "m"), ("feminine", "f"), ("neuter", "n")):
                    if key in tags:
                        _add(past[g], form)
            continue
        person = None
        for key, n in (("first-person", "1"), ("second-person", "2"), ("third-person", "3")):
            if key in tags:
                person = ("pl" if "plural" in tags else "sg") + n
        if person is None:
            continue
        if "present" in tags:
            _add(pres[person], form)
        elif "future" in tags:
            _add(fut[person], form)

    for group in (pres, fut, past, imp, part):
        for k in group:
            group[k] = tidy(group[k])
    inf = tidy(inf)
    aspects = (head_tags | table_aspects) & {"imperfective", "perfective"}
    has_pres = any(pres.values())
    has_fut = any(fut.values())
    if not inf and not has_pres and not has_fut and not any(past.values()):
        return None
    if not inf:
        inf = [ensure_stress(lemma)]

    if aspects == {"imperfective", "perfective"}:
        aspect = "both"
    elif aspects == {"perfective"}:
        aspect = "pf"
    elif aspects == {"imperfective"}:
        aspect = "impf"
    else:
        aspect = "pf" if (has_fut and not has_pres) else "impf"

    if aspect == "both" and not has_fut:
        fut = {p: list(v) for p, v in pres.items()}
        has_fut = has_pres

    paradigm: dict = {"kind": "verb", "aspect": aspect, "infinitive": inf}
    cells: list[tuple[str, str, str]] = []
    emit(cells, "inf", inf, "Инфинитив")
    if has_pres:
        paradigm["present"] = pres
        for p in PERSON_KEYS:
            emit(cells, f"pres.{p}", pres[p], f"Наст., {PERSON_LABELS[p]}")
    if has_fut:
        paradigm["future"] = fut
        for p in PERSON_KEYS:
            emit(cells, f"fut.{p}", fut[p], f"Будущ., {PERSON_LABELS[p]}")
    paradigm["past"] = past
    for g in ("m", "f", "n", "pl"):
        label = "Прош., мн. ч." if g == "pl" else f"Прош., {GENDER_LABELS[g]}, ед. ч."
        emit(cells, f"past.{g}", past[g], label)
    paradigm["imperative"] = imp
    for n in ("sg", "pl"):
        emit(cells, f"imp.{n}", imp[n], f"Повелит., {NUMBER_LABELS[n].lower()}")
    paradigm["participles"] = part
    for k in PARTICIPLE_KEYS:
        emit(cells, f"part.{k}", part[k], PARTICIPLE_LABELS[k])

    partners: list[str] = []
    for tag in ("perfective", "imperfective"):
        for f in head_forms(entry, tag):
            if lemma_clean(f) != lemma_clean(lemma):
                _add(partners, f)
    if partners:
        paradigm["partners"] = partners[:6]
    return Parsed("verb", paradigm, cells, aspect=ASPECT_LABELS[aspect])


# ----------------------------------------------------------------------- dispatch

def parse_entry(entry: dict) -> Parsed | None:
    pos = entry.get("pos")
    if pos in ("noun", "name"):
        return parse_noun(entry)
    if pos == "verb":
        return parse_verb(entry)
    if pos == "adj":
        return parse_adjective(entry)
    if pos in ("pron", "det", "num"):
        return parse_adjective(entry, stress_monosyllables=False) or parse_simple(entry) or _noun_like(entry)
    return None


def _noun_like(entry: dict) -> Parsed | None:
    """Numeralia/pronomina dengan tabel sg/pl seperti kata benda (тысяча, миллион)."""
    parsed = parse_noun(entry)
    return parsed


def english_gloss(entry: dict, limit: int = 140) -> str:
    """Ringkasan arti bahasa Inggris (cadangan bila belum ada terjemahan Indonesia)."""
    out: list[str] = []
    senses = entry.get("senses") or ()
    # Lewati arti "bentuk dari ..." kecuali bila hanya itu yang ada (mis. "female equivalent of студент").
    only_form_of = all("form-of" in (s.get("tags") or ()) for s in senses)
    for sense in senses:
        tags = sense.get("tags") or ()
        if "form-of" in tags and not only_form_of:
            continue
        glosses = sense.get("glosses") or ()
        if not glosses:
            continue
        gloss = re.sub(r"\s*\([^)]*\)", "", glosses[-1]).strip(" .;:")
        if gloss and gloss not in out:
            out.append(gloss)
        if len(out) >= 4:
            break
    text = "; ".join(out)
    if len(text) > limit:
        text = text[: limit - 1].rsplit(" ", 1)[0] + "…"
    return text
