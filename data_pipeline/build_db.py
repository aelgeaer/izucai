"""Bangun `russian_morphology.db` -- basis data morfologi Rusia offline untuk aplikasi Android.

Alur:
  1. Baca dump Kaikki (Wiktionary) -> urai tabel deklinasi/konjugasi tiap lema (kaikki_parser.py)
  2. Timpa kelas kata tertutup / tak beraturan dengan paradigma tulisan tangan (special_cases.py)
  3. Pasangkan dengan kamus kurasi Rusia->Indonesia (translations/*.txt)
  4. Hitung frekuensi tiap lema, pilih: semua lema kurasi + N lema tersering lainnya
  5. Tulis SQLite: lemmas, word_forms (reverse lookup), id_search_index (pencarian Indonesia)

Jalankan:
    python data_pipeline/download_sources.py
    python data_pipeline/build_db.py [--extended 20000] [--no-copy]
"""
from __future__ import annotations

import argparse
import gzip
import json
import re
import shutil
import sqlite3
import sys
import time
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import special_cases  # noqa: E402
from kaikki_parser import Parsed, canonical_form, english_gloss, parse_entry  # noqa: E402
from textnorm import ensure_stress, is_plain_cyrillic, is_pre_reform, lemma_clean, normalize_search, strip_stress  # noqa: E402

KAIKKI_FILE = HERE / "sources" / "kaikki" / "kaikki-russian.jsonl.gz"
FREQ_FILE = HERE / "sources" / "freq" / "ru_full.txt"
TRANSLATIONS_DIR = HERE / "translations"
STAGE_DB = HERE / "build" / "stage.db"
OUTPUT_DB = HERE / "russian_morphology.db"
ASSETS_DB = HERE.parent / "app" / "src" / "main" / "assets" / "russian_morphology.db"

SCHEMA_VERSION = 1

POS_CLASS = {"noun": "NOUN", "name": "NOUN", "verb": "VERB", "adj": "ADJ", "pron": "PRON", "det": "PRON", "num": "NUM"}
# Berkas terjemahan -> kelas kata yang dicoba berurutan saat mencocokkan.
TRANSLATION_FILES = {
    "nouns": ["NOUN"],
    "verbs": ["VERB"],
    "adjectives": ["ADJ", "PRON", "NUM"],
    "pronouns": ["PRON", "NUM", "ADJ", "NOUN"],
}
# Kelas kata tak berinfleksi: bentuknya (и, в, да, хорошо ...) tidak dihitung sebagai bukti
# frekuensi sebuah lema, supaya mis. nama huruf "в" tidak dianggap kata benda tersering.
FUNCTION_POS = {"prep", "conj", "particle", "adv", "intj", "postp", "prep_phrase", "character", "symbol"}
ID_STOPWORDS = {
    "yang", "di", "ke", "dari", "dan", "atau", "untuk", "dengan", "pada", "oleh", "sebagai", "se",
    "itu", "ini", "bagi", "akan", "sampai", "lalu", "agar", "dalam", "atas", "per",
}
_WORD_RE = re.compile(r"[a-z]+(?:-[a-z]+)*")
_PAREN_RE = re.compile(r"\(([^)]*)\)")


def log(msg: str) -> None:
    print(msg, flush=True)


# ------------------------------------------------------------------ kamus kurasi

_SENSE_RE = re.compile(r"^(?P<lemma>[^\[]+?)\s*(?:\[(?P<sense>[^\]]+)\])?$")
_ALT_FORM_PREFIXES = ("alternative form", "alternative spelling", "obsolete form", "dated form", "misspelling")


@dataclass
class CuratedEntry:
    stressed: str | None   # lemma bertanda tekan bila ditulis (pembeda homograf за́мок / замо́к)
    sense: str | None      # kata kunci arti bahasa Inggris (pembeda homonim: покупать [bathe])
    translations: list[str]
    order: int


def load_translations() -> dict[tuple[str, str], list[CuratedEntry]]:
    """{(grup berkas, lemma ternormalisasi): [CuratedEntry]}

    Format baris:  лемма = terjemahan, ...
                   лемма [kata kunci arti Inggris] = terjemahan, ...
    Kata kunci arti memilih lema yang arti Wiktionary-nya (gloss_en) cocok, untuk ejaan yang
    dimiliki beberapa kata berbeda: «покупать [buy] = membeli», «покупать [bathe] = memandikan».
    """
    out: dict[tuple[str, str], list[CuratedEntry]] = defaultdict(list)
    order = 0
    for path in sorted(TRANSLATIONS_DIR.glob("*.txt")):
        group = path.stem.split("_")[0]
        if group not in TRANSLATION_FILES:
            raise SystemExit(f"Berkas terjemahan tak dikenal: {path.name}")
        for lineno, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            if " = " not in line:
                raise SystemExit(f"{path.name}:{lineno}: format harus 'лемма = terjemahan'")
            head, _, trans = line.partition(" = ")
            match = _SENSE_RE.match(head.strip())
            if not match:
                raise SystemExit(f"{path.name}:{lineno}: lemma tidak sah: {head!r}")
            lemma = match["lemma"].strip()
            sense = match["sense"].strip().lower() if match["sense"] else None
            translations = [t.strip() for t in trans.split(",") if t.strip()]
            stressed = lemma if "́" in lemma else None
            key = (group, normalize_search(lemma))
            for existing in out[key]:
                if existing.stressed == stressed and existing.sense == sense:  # entri ganda -> gabungkan
                    existing.translations += [t for t in translations if t not in existing.translations]
                    break
            else:
                order += 1
                out[key].append(CuratedEntry(stressed, sense, translations, order))
    return out


def pick_candidates(rows: list[tuple], entry: CuratedEntry) -> list[tuple]:
    """Pilih lema yang menerima terjemahan kurasi.

    rows: (id, lemma_stressed, gloss_en, score, is_name), urut skor menurun.
    - nama diri (Ве́ра, А́вгуст) dan entri "alternative form of ..." disisihkan;
    - dengan kata kunci arti: gloss sama persis > gloss diawali kata kunci > gloss memuat kata kunci;
    - tanpa kata kunci: hanya lema yang paling sering dipakai. Homonim lain (покупа́ть СВ
      "memandikan sebentar") tidak ikut mendapat terjemahan "membeli".
    """
    if entry.stressed:
        rows = [r for r in rows if r[1] == entry.stressed]
    common = [r for r in rows
              if not r[4] and not r[1][:1].isupper() and not r[2].lower().startswith(_ALT_FORM_PREFIXES)]
    rows = common or rows
    if entry.sense:
        glosses = [(r, r[2].lower()) for r in rows]
        for test in (lambda g: g == entry.sense, lambda g: g.startswith(entry.sense), lambda g: entry.sense in g):
            hit = [r for r, g in glosses if test(g)]
            if hit:
                return hit
        return []
    return rows[:1]


# ------------------------------------------------------------- tahap 1: kandidat

STAGE_SCHEMA = """
CREATE TABLE cand (
    id INTEGER PRIMARY KEY,
    norm TEXT NOT NULL,
    pos TEXT NOT NULL,
    lemma_stressed TEXT NOT NULL,
    gender TEXT,
    aspect TEXT,
    gloss_en TEXT NOT NULL,
    paradigm_json TEXT NOT NULL,
    vulgar INTEGER NOT NULL DEFAULT 0,
    is_name INTEGER NOT NULL DEFAULT 0,
    special INTEGER NOT NULL DEFAULT 0,
    score REAL NOT NULL DEFAULT 0
);
CREATE TABLE function_words (form TEXT PRIMARY KEY) WITHOUT ROWID;
CREATE TABLE cand_forms (
    cand_id INTEGER NOT NULL,
    form_clean TEXT NOT NULL,
    form_stressed TEXT NOT NULL,
    cell_key TEXT NOT NULL,
    gram_info TEXT NOT NULL
);
"""


def mark_rare(parsed: Parsed, norm: str) -> None:
    rare = special_cases.RARE_FORMS.get(norm)
    if not rare or parsed.kind != "verb":
        return
    part = parsed.paradigm.get("participles", {})
    for key, bare_forms in rare.items():
        part[key] = [("*" + f if strip_stress(f) in bare_forms else f) for f in part.get(key, [])]


def is_lemma_entry(entry: dict) -> bool:
    """False untuk entri "bentuk infleksi" (noun form, verb form, ...) yang bukan kata dasar."""
    for head in entry.get("head_templates") or ():
        if str((head.get("args") or {}).get("2", "")).endswith(" form"):
            return False
    return True


def table_matches_lemma(parsed: Parsed, norm: str) -> bool:
    """Pastikan tabel memang milik lema ini (bentuk kamusnya harus muncul di sel utama)."""
    p = parsed.paradigm
    if parsed.kind == "verb":
        base = p["infinitive"]
    elif parsed.kind == "noun":
        nom = p["rows"][0]
        base = nom["sg"] or nom["pl"]
    elif parsed.kind == "adj":
        base = p["rows"][0]["m"] + p["rows"][0]["pl"]
    else:
        base = p["rows"][0]["forms"]
    return any(normalize_search(f.lstrip("*")) == norm for f in base)


def insert_candidate(db: sqlite3.Connection, norm: str, pos: str, lemma_stressed: str, parsed: Parsed,
                     gloss: str, *, vulgar: bool = False, is_name: bool = False, special: bool = False) -> int:
    cur = db.execute(
        "INSERT INTO cand (norm, pos, lemma_stressed, gender, aspect, gloss_en, paradigm_json, vulgar, is_name, special)"
        " VALUES (?,?,?,?,?,?,?,?,?,?)",
        (norm, pos, lemma_stressed, parsed.gender, parsed.aspect, gloss,
         json.dumps(parsed.paradigm, ensure_ascii=False, separators=(",", ":")), int(vulgar), int(is_name), int(special)),
    )
    cand_id = cur.lastrowid
    rows = []
    for cell_key, form, info in parsed.cells:
        if " " in form:
            continue  # bentuk berpreposisi (ни о ко́м) hanya ditampilkan, tidak diindeks
        rows.append((cand_id, normalize_search(form), form, cell_key, info))
    db.executemany("INSERT INTO cand_forms VALUES (?,?,?,?,?)", rows)
    return cand_id


def build_stage(limit: int | None = None) -> sqlite3.Connection:
    STAGE_DB.parent.mkdir(parents=True, exist_ok=True)
    if STAGE_DB.exists():
        STAGE_DB.unlink()
    db = sqlite3.connect(STAGE_DB)
    db.executescript("PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF;" + STAGE_SCHEMA)

    specials = special_cases.all_special_pronouns()
    special_norms = {norm for norm, _ in specials}
    byt_lemma, byt_parsed = special_cases.byt()
    seen: dict[tuple[str, str, str, str], int] = {}  # (norm, pos, lemma, paradigma) -> cand id
    stats: dict[str, int] = defaultdict(int)
    started = time.time()

    def consider(entry: dict, pos: str, *, borrowed: bool = False) -> bool:
        """Urai satu entri; simpan sebagai kandidat bila tabelnya sah. True = tersimpan."""
        word = entry["word"]
        norm = normalize_search(word)
        try:
            parsed = parse_entry(entry)
        except Exception as exc:  # satu entri rusak tidak boleh menggagalkan seluruh build
            stats["gagal_urai"] += 1
            if stats["gagal_urai"] <= 5:
                log(f"  ! gagal mengurai {word!r}: {exc!r}")
            return False
        if parsed is None or not table_matches_lemma(parsed, norm):
            return False
        lemma_stressed, _ = canonical_form(entry)
        if lemma_clean(lemma_stressed) != lemma_clean(word):
            lemma_stressed = word
        if pos in ("NOUN", "VERB", "ADJ"):
            lemma_stressed = ensure_stress(lemma_stressed)
        mark_rare(parsed, norm)
        if pos == "ADJ" and norm in special_cases.COMPARATIVE_OVERRIDES and parsed.paradigm.get("comparative"):
            fixed = special_cases.COMPARATIVE_OVERRIDES[norm]
            parsed.paradigm["comparative"] = fixed
            parsed.cells = [c for c in parsed.cells if c[0] != "comp"] + [("comp", f, "Сравн. ст.") for f in fixed]
        gloss = english_gloss(entry)
        senses = [s for s in entry.get("senses", ()) if "form-of" not in (s.get("tags") or ())]
        vulgar = bool(senses) and all("vulgar" in (s.get("tags") or ()) for s in senses)

        pj = json.dumps(parsed.paradigm, ensure_ascii=False, sort_keys=True)
        key = (norm, pos, lemma_stressed, pj)
        if key in seen:  # homonim dengan paradigma identik (мир "dunia" / мир "damai") -> satu lema
            if gloss:
                old = db.execute("SELECT gloss_en FROM cand WHERE id=?", (seen[key],)).fetchone()[0]
                if gloss not in old and len(old) < 200:
                    db.execute("UPDATE cand SET gloss_en=? WHERE id=?", ((old + "; " + gloss).strip("; "), seen[key]))
            stats["digabung"] += 1
            return True
        seen[key] = insert_candidate(db, norm, pos, lemma_stressed, parsed, gloss,
                                     vulgar=vulgar, is_name=entry.get("pos") == "name")
        stats["kandidat"] += 1
        if borrowed:
            stats["tabel_dipinjam"] += 1
        return True

    # Kaikki menempelkan tabel infleksi ke bagian kelas kata TERAKHIR di sebuah halaman Wiktionary.
    # Akibatnya, mis. tabel konjugasi "звучать" tercatat di entri predikatif dan entri kata kerjanya
    # kosong. Tabel "nyasar" dikumpulkan sebagai donor, lalu dipinjamkan ke entri yang membutuhkannya.
    function_words: set[str] = set()
    donors: dict[str, list[list[dict]]] = defaultdict(list)
    pending: list[tuple[dict, str]] = []

    with gzip.open(KAIKKI_FILE, "rt", encoding="utf-8") as fh:
        for n, line in enumerate(fh, 1):
            if limit and n > limit:
                break
            if n % 100_000 == 0:
                log(f"  {n:>7} entri dibaca, {stats['kandidat']} kandidat ({time.time() - started:.0f} dtk)")
            entry = json.loads(line)
            word = entry.get("word") or ""
            if entry.get("lang_code") != "ru" or " " in word:
                continue
            clean = lemma_clean(word)
            if not is_plain_cyrillic(clean) or clean.startswith("-") or clean.endswith("-") or is_pre_reform(word):
                continue
            pos = POS_CLASS.get(entry.get("pos"))
            norm = normalize_search(word)
            if entry.get("pos") in FUNCTION_POS and is_lemma_entry(entry):
                function_words.add(norm)
            table = [f for f in entry.get("forms", ()) if f.get("source") in ("declension", "conjugation")]
            if (pos in ("PRON", "NUM", "ADJ") and norm in special_norms) or (norm == "быть" and pos == "VERB"):
                continue  # ditimpa paradigma tulisan tangan
            if pos and is_lemma_entry(entry) and consider(entry, pos):
                continue
            if table:
                donors[word].append(table)
            elif pos and is_lemma_entry(entry):
                entry.pop("etymology_templates", None)
                entry.pop("sounds", None)
                pending.append((entry, pos))
            else:
                stats["tanpa_tabel"] += 1

    for entry, pos in pending:
        for table in donors.get(entry["word"], ()):
            if consider({**entry, "forms": list(entry.get("forms", ())) + table}, pos, borrowed=True):
                break
        else:
            stats["tanpa_tabel"] += 1

    insert_candidate(db, "быть", "VERB", byt_lemma, byt_parsed, "to be; there is, there are", special=True)
    for (norm, pos), (lemma, parsed) in specials.items():
        insert_candidate(db, norm, pos, lemma, parsed, "", special=True)
    db.executemany("INSERT OR IGNORE INTO function_words VALUES (?)", [(w,) for w in function_words])
    db.execute("CREATE INDEX cf_form ON cand_forms(form_clean)")
    db.execute("CREATE INDEX cf_cand ON cand_forms(cand_id)")
    db.execute("CREATE INDEX cand_norm ON cand(norm, pos)")
    db.commit()
    log(f"Kandidat: {dict(stats)}")
    return db


def compute_scores(db: sqlite3.Connection) -> None:
    """Skor frekuensi lema = jumlah frekuensi bentuk-bentuknya di korpus.

    Bentuk yang dimiliki beberapa lema (homograf) dibagi sebanding dengan frekuensi bentuk-bentuk
    "eksklusif" tiap lema: «какой» hampir seluruhnya milik pronomina какой, bukan kata benda кака.
    """
    if not FREQ_FILE.exists():
        log("! daftar frekuensi tidak ada -- semua skor = 0 (jalankan download_sources.py)")
        return
    counts: dict[str, int] = defaultdict(int)
    with open(FREQ_FILE, encoding="utf-8") as fh:
        for line in fh:
            word, _, cnt = line.rstrip("\n").rpartition(" ")
            if cnt.isdigit() and int(cnt) >= 3:
                counts[normalize_search(word)] += int(cnt)
    function_words = {r[0] for r in db.execute("SELECT form FROM function_words")}

    # Bentuk yang juga merupakan kata fungsi (как, так, для, просто, в ...) bukan bukti frekuensi:
    # kemunculan «как» di korpus adalah kata keterangan, bukan genitif jamak dari «кака».
    owners: dict[str, list[int]] = defaultdict(list)
    for cand_id, form in db.execute("SELECT DISTINCT cand_id, form_clean FROM cand_forms"):
        if form in counts and form not in function_words:
            owners[form].append(cand_id)

    exclusive: dict[int, float] = defaultdict(float)
    for form, ids in owners.items():
        if len(ids) == 1:
            exclusive[ids[0]] += counts[form]
    score: dict[int, float] = defaultdict(float, exclusive)
    for form, ids in owners.items():
        if len(ids) > 1:
            weights = [exclusive[i] + 1.0 for i in ids]
            total = sum(weights)
            for cand_id, weight in zip(ids, weights):
                score[cand_id] += counts[form] * weight / total

    db.execute("UPDATE cand SET score = 0")
    db.executemany("UPDATE cand SET score = ? WHERE id = ?", [(v, k) for k, v in score.items()])
    db.commit()


# ------------------------------------------------------- tahap 2: basis data akhir

FINAL_SCHEMA = """
CREATE TABLE lemmas (
    id INTEGER PRIMARY KEY,
    lemma_clean TEXT NOT NULL,       -- huruf kecil tanpa tanda tekan ("машина", "ёлка")
    lemma_norm TEXT NOT NULL,        -- kunci pencarian: ё -> е ("елка")
    lemma_stressed TEXT NOT NULL,    -- dengan tanda tekan U+0301 ("маши́на")
    pos TEXT NOT NULL,               -- NOUN | VERB | ADJ | PRON | NUM
    aspect TEXT,                     -- Несовершенный вид (НСВ) | Совершенный вид (СВ) | ...
    gender TEXT,                     -- муж. | жен. | ср.
    translations_id TEXT NOT NULL,   -- terjemahan Indonesia dipisah koma ("" bila belum ada)
    translations_en TEXT NOT NULL,   -- arti bahasa Inggris (cadangan, dari Wiktionary)
    paradigm_json TEXT NOT NULL,     -- seluruh tabel perubahan kata
    freq_rank INTEGER NOT NULL       -- 1 = lema paling sering dipakai
);
CREATE TABLE word_forms (
    form_clean TEXT NOT NULL,        -- huruf kecil, ё -> е, tanpa tanda tekan
    form_stressed TEXT NOT NULL,     -- bentuk asli bertanda tekan
    lemma_id INTEGER NOT NULL REFERENCES lemmas(id),
    gram_info TEXT NOT NULL,         -- "Твор., Мн. ч." (beberapa posisi dipisah "; ")
    cell_keys TEXT NOT NULL          -- posisi sel di tabel: "ins.pl" (dipisah ";")
);
CREATE TABLE id_search_index (
    indo_word TEXT NOT NULL,         -- kata/frasa Indonesia huruf kecil
    lemma_id INTEGER NOT NULL REFERENCES lemmas(id),
    priority INTEGER NOT NULL        -- 0 = terjemahan utama; makin besar makin lemah
);
CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""
FINAL_INDEXES = """
CREATE INDEX idx_word_forms_form_clean ON word_forms(form_clean);
CREATE INDEX idx_word_forms_lemma ON word_forms(lemma_id);
CREATE INDEX idx_lemmas_norm ON lemmas(lemma_norm);
CREATE INDEX idx_id_search_indo_word ON id_search_index(indo_word);
"""


def indo_index_entries(translations: list[str]) -> list[tuple[str, int]]:
    """Kata kunci Indonesia + prioritas untuk satu lema."""
    best: dict[str, int] = {}

    def put(word: str, prio: int) -> None:
        if word and (word not in best or prio < best[word]):
            best[word] = prio

    for pos, raw in enumerate(translations):
        raw = raw.lower()
        notes = " ".join(_PAREN_RE.findall(raw))
        main = _PAREN_RE.sub(" ", raw)
        main = re.sub(r"\s+", " ", main).strip(" -")
        put(main, pos)                                   # frasa utuh: "makan siang"
        tokens = _WORD_RE.findall(main)
        if len(tokens) > 1:
            for tok in tokens:                           # kata di dalam frasa: "makan", "siang"
                if tok not in ID_STOPWORDS and len(tok) > 1:
                    put(tok, 100 + pos)
        for tok in _WORD_RE.findall(notes):              # kata di dalam catatan kurung
            if tok not in ID_STOPWORDS and len(tok) > 2:
                put(tok, 200 + pos)
    return sorted(best.items(), key=lambda kv: kv[1])


def select_and_write(stage: sqlite3.Connection, translations, extended: int, min_score: float) -> dict:
    # --- cocokkan kamus kurasi dengan kandidat
    cand_translations: dict[int, tuple[list[str], int]] = {}
    missing: list[str] = []
    for (group, norm), items in translations.items():
        promote: list[int] = []  # lema arti utama (baris kurasi pertama) per ejaan
        for entry in items:
            trans, order = entry.translations, entry.order
            rows = []
            for pos in TRANSLATION_FILES[group]:
                rows = stage.execute(
                    "SELECT id, lemma_stressed, gloss_en, score, is_name FROM cand WHERE norm=? AND pos=? "
                    "ORDER BY score DESC, id", (norm, pos)).fetchall()
                rows = pick_candidates(rows, entry)
                if rows:
                    break
            if not rows:
                missing.append(f"{group}:{entry.stressed or norm}" + (f" [{entry.sense}]" if entry.sense else ""))
                continue
            if not promote and entry.sense:
                promote = [r[0] for r in rows]
            for cand_id, *_ in rows:
                if cand_id in cand_translations:
                    merged = cand_translations[cand_id][0] + [t for t in trans if t not in cand_translations[cand_id][0]]
                    cand_translations[cand_id] = (merged, cand_translations[cand_id][1])
                else:
                    cand_translations[cand_id] = (list(trans), order)
        if promote:
            # Homonim berejaan sama: arti yang ditulis pertama di kamus kurasi (arti umum) selalu
            # tampil di atas arti lain, walau skor frekuensinya terbagi (му́сор "sampah" > "polisi").
            ph = ",".join("?" * len(promote))
            top = stage.execute(
                f"SELECT MAX(score) FROM cand WHERE norm=? AND id NOT IN ({ph})", (norm, *promote)).fetchone()[0]
            if top is not None:
                stage.execute(
                    f"UPDATE cand SET score = MAX(score, ? + 1) WHERE id IN ({ph})", (top, *promote))

    # --- pilih lema: kurasi + khusus + N tersering lainnya
    chosen: set[int] = set(cand_translations)
    chosen |= {r[0] for r in stage.execute("SELECT id FROM cand WHERE special=1")}
    extra = stage.execute(
        "SELECT id FROM cand WHERE vulgar=0 AND score>=? ORDER BY score DESC", (min_score,)
    ).fetchall()
    added = 0
    for (cand_id,) in extra:
        if added >= extended:
            break
        if cand_id not in chosen:
            chosen.add(cand_id)
            added += 1

    ordered = stage.execute(
        f"SELECT id, norm, pos, lemma_stressed, gender, aspect, gloss_en, paradigm_json, score FROM cand "
        f"WHERE id IN ({','.join(map(str, chosen))}) ORDER BY score DESC, norm"
    ).fetchall()

    # --- tulis basis data akhir
    if OUTPUT_DB.exists():
        OUTPUT_DB.unlink()
    out = sqlite3.connect(OUTPUT_DB)
    out.executescript("PRAGMA journal_mode=OFF; PRAGMA synchronous=OFF; PRAGMA page_size=4096;" + FINAL_SCHEMA)
    pos_counts: dict[str, int] = defaultdict(int)
    n_forms = 0
    for rank, (cand_id, norm, pos, lemma_stressed, gender, aspect, gloss, pj, _score) in enumerate(ordered, 1):
        trans = cand_translations.get(cand_id, ([], 0))[0]
        out.execute(
            "INSERT INTO lemmas VALUES (?,?,?,?,?,?,?,?,?,?,?)",
            (rank, lemma_clean(lemma_stressed), norm, lemma_stressed, pos, aspect, gender,
             ", ".join(trans), gloss, pj, rank),
        )
        pos_counts[pos] += 1
        grouped: dict[tuple[str, str], tuple[list[str], list[str]]] = {}
        for form_clean, form_stressed, cell_key, info in stage.execute(
            "SELECT form_clean, form_stressed, cell_key, gram_info FROM cand_forms WHERE cand_id=? ORDER BY rowid", (cand_id,)
        ):
            infos, keys = grouped.setdefault((form_clean, form_stressed), ([], []))
            if info not in infos:
                infos.append(info)
            if cell_key not in keys:
                keys.append(cell_key)
        out.executemany(
            "INSERT INTO word_forms VALUES (?,?,?,?,?)",
            [(fc, fs, rank, "; ".join(infos), ";".join(keys)) for (fc, fs), (infos, keys) in grouped.items()],
        )
        n_forms += len(grouped)
        out.executemany(
            "INSERT INTO id_search_index VALUES (?,?,?)",
            [(word, rank, prio) for word, prio in indo_index_entries(trans)],
        )

    summary = {
        "schema_version": SCHEMA_VERSION,
        "build_date": time.strftime("%Y-%m-%d"),
        "lemma_count": len(ordered),
        "form_count": n_forms,
        "translated_count": len(cand_translations),
        **{f"count_{pos}": n for pos, n in sorted(pos_counts.items())},
        "sources": "Wiktionary via Kaikki.org (CC BY-SA 4.0); FrequencyWords/OpenSubtitles 2018 (CC BY-SA 4.0)",
    }
    out.executemany("INSERT INTO meta VALUES (?,?)", [(k, str(v)) for k, v in summary.items()])
    out.executescript(FINAL_INDEXES)
    out.commit()
    out.execute("VACUUM")
    out.close()
    summary["missing"] = missing
    return summary


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--extended", type=int, default=20000,
                    help="jumlah lema tambahan (di luar kamus kurasi) yang dipilih menurut frekuensi")
    ap.add_argument("--min-score", type=float, default=1.0, help="skor frekuensi minimum untuk lema tambahan")
    ap.add_argument("--reuse-stage", action="store_true", help="pakai build/stage.db yang sudah ada (lewati pengurai)")
    ap.add_argument("--no-copy", action="store_true", help="jangan salin hasil ke app/src/main/assets")
    args = ap.parse_args()

    if not KAIKKI_FILE.exists() and not args.reuse_stage:
        log(f"Sumber belum ada: {KAIKKI_FILE}\nJalankan dulu: python data_pipeline/download_sources.py")
        return 1

    translations = load_translations()
    log(f"Kamus kurasi: {sum(len(v) for v in translations.values())} entri")

    if args.reuse_stage and STAGE_DB.exists():
        stage = sqlite3.connect(STAGE_DB)
    else:
        log("Mengurai dump Kaikki ...")
        stage = build_stage()
        log("Menghitung frekuensi lema ...")
        compute_scores(stage)

    log("Menulis basis data akhir ...")
    summary = select_and_write(stage, translations, args.extended, args.min_score)
    stage.close()

    missing = summary.pop("missing")
    for k, v in summary.items():
        log(f"  {k}: {v}")
    log(f"  ukuran: {OUTPUT_DB.stat().st_size / 1e6:.1f} MB -> {OUTPUT_DB}")
    if missing:
        log(f"  ! {len(missing)} entri kurasi tidak ditemukan di Wiktionary:")
        log("    " + ", ".join(missing))
    if not args.no_copy and ASSETS_DB.parent.exists():
        shutil.copyfile(OUTPUT_DB, ASSETS_DB)
        log(f"  disalin ke {ASSETS_DB}")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")  # konsol Windows: cetak Sirilik dengan benar
    sys.exit(main())
