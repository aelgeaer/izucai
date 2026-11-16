"""Verifikasi `russian_morphology.db` dengan kueri SQL (skenario wajib Tahap 1 & 6).

Jalankan:  python data_pipeline/verify_db.py [-v]
Kode keluar 0 bila semua pemeriksaan lulus.
"""
from __future__ import annotations

import json
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from textnorm import normalize_search  # noqa: E402

DB = HERE / "russian_morphology.db"
S = "́"  # tanda tekan
failures: list[str] = []
verbose = "-v" in sys.argv


def check(name: str, ok: bool, detail: str = "") -> None:
    print(f"  [{'OK' if ok else 'GAGAL'}] {name}" + (f"  -- {detail}" if detail and (verbose or not ok) else ""))
    if not ok:
        failures.append(name)


def lookup_ru(db: sqlite3.Connection, text: str) -> list[sqlite3.Row]:
    """Reverse lookup: bentuk kata apa pun -> lema."""
    return db.execute(
        "SELECT l.*, w.form_stressed, w.gram_info, w.cell_keys FROM word_forms w JOIN lemmas l ON l.id = w.lemma_id "
        "WHERE w.form_clean = ? ORDER BY l.freq_rank",
        (normalize_search(text),),
    ).fetchall()


def lookup_id(db: sqlite3.Connection, word: str) -> list[sqlite3.Row]:
    return db.execute(
        "SELECT l.*, i.priority FROM id_search_index i JOIN lemmas l ON l.id = i.lemma_id "
        "WHERE i.indo_word = ? ORDER BY i.priority, l.freq_rank",
        (word.lower(),),
    ).fetchall()


def find(rows, lemma: str, pos: str):
    return next((r for r in rows if r["lemma_clean"] == lemma and r["pos"] == pos), None)


def main() -> int:
    sys.stdout.reconfigure(encoding="utf-8")
    db = sqlite3.connect(f"file:{DB.as_posix()}?mode=ro", uri=True)
    db.row_factory = sqlite3.Row

    print("Struktur & isi")
    meta = dict(db.execute("SELECT key, value FROM meta").fetchall())
    n_lemmas = db.execute("SELECT COUNT(*) FROM lemmas").fetchone()[0]
    n_forms = db.execute("SELECT COUNT(*) FROM word_forms").fetchone()[0]
    n_translated = db.execute("SELECT COUNT(*) FROM lemmas WHERE translations_id != ''").fetchone()[0]
    check("jumlah lema >= 3000", n_lemmas >= 3000, f"{n_lemmas} lema, {n_forms} bentuk kata")
    check("lema berterjemahan Indonesia >= 3000", n_translated >= 3000, str(n_translated))
    for pos in ("NOUN", "VERB", "ADJ", "PRON", "NUM"):
        n = db.execute("SELECT COUNT(*) FROM lemmas WHERE pos=? AND translations_id != ''", (pos,)).fetchone()[0]
        check(f"ada lema {pos} berterjemahan", n > 0, str(n))
    plan = " ".join(r[3] for r in db.execute("EXPLAIN QUERY PLAN SELECT * FROM word_forms WHERE form_clean = 'x'"))
    check("pencarian form_clean memakai indeks", "idx_word_forms_form_clean" in plan, plan)
    plan = " ".join(r[3] for r in db.execute("EXPLAIN QUERY PLAN SELECT * FROM id_search_index WHERE indo_word = 'x'"))
    check("pencarian indo_word memakai indeks", "idx_id_search_indo_word" in plan, plan)
    bad = db.execute("SELECT COUNT(*) FROM word_forms WHERE form_clean LIKE '%ё%' OR form_clean LIKE '%' || char(769) || '%'").fetchone()[0]
    check("form_clean ternormalisasi (tanpa ё / tanda tekan)", bad == 0, f"{bad} baris bermasalah")
    orphan = db.execute("SELECT COUNT(*) FROM lemmas l WHERE NOT EXISTS (SELECT 1 FROM word_forms w WHERE w.lemma_id = l.id)").fetchone()[0]
    check("setiap lema punya bentuk kata", orphan == 0, f"{orphan} lema tanpa bentuk")
    invalid = 0
    for (pj,) in db.execute("SELECT paradigm_json FROM lemmas"):
        if json.loads(pj).get("kind") not in ("noun", "verb", "adj", "simple"):
            invalid += 1
    check("semua paradigm_json sah", invalid == 0, f"{invalid} tidak sah")

    print("Kata benda: машина")
    row = find(lookup_ru(db, "машина"), "машина", "NOUN")
    check("машина -> lema NOUN", row is not None)
    if row:
        p = json.loads(row["paradigm_json"])
        cases = {r["case"]: r for r in p["rows"]}
        check("lemma_stressed = маши́на", row["lemma_stressed"] == f"маши{S}на", row["lemma_stressed"])
        check("gender = жен.", row["gender"] == "жен.", str(row["gender"]))
        check("6 kasus x 2 bilangan", all(cases[c]["sg"] and cases[c]["pl"] for c in ("nom", "gen", "dat", "acc", "ins", "prep")))
        check("Твор. ед. = маши́ной / маши́ною", cases["ins"]["sg"] == [f"маши{S}ной", f"маши{S}ною"], str(cases["ins"]["sg"]))
        check("Род. мн. = маши́н", cases["gen"]["pl"] == [f"маши{S}н"], str(cases["gen"]["pl"]))
        check("terjemahan memuat 'mobil'", "mobil" in row["translations_id"], row["translations_id"])
    rows = lookup_ru(db, "машинами")
    row = find(rows, "машина", "NOUN")
    check("машинами -> машина (Твор., Мн. ч.)", bool(row) and row["gram_info"] == "Твор., Мн. ч." and row["cell_keys"] == "ins.pl",
          str([(r["lemma_clean"], r["gram_info"]) for r in rows]))
    row = find(lookup_ru(db, "машиною"), "машина", "NOUN")
    check("машиною -> машина", row is not None)

    print("Kata kerja: заказать")
    for text, info in (("заказать", "Инфинитив"), ("заказал", "Прош., муж., ед. ч."), ("заказала", "Прош., жен., ед. ч."), ("закажу", "Будущ., 1 л., ед. ч.")):
        rows = lookup_ru(db, text)
        row = find(rows, "заказать", "VERB")
        check(f"{text} -> заказать ({info})", bool(row) and row["gram_info"] == info, str([(r["lemma_clean"], r["gram_info"]) for r in rows]))
    row = find(lookup_ru(db, "заказать"), "заказать", "VERB")
    if row:
        p = json.loads(row["paradigm_json"])
        check("aspek = Совершенный вид (СВ)", row["aspect"] == "Совершенный вид (СВ)" and p["aspect"] == "pf", str(row["aspect"]))
        check("СВ: ada будущ., tanpa наст.", "future" in p and "present" not in p)
        check("будущ. я = закажу́", p["future"]["sg1"] == [f"закажу{S}"], str(p["future"]["sg1"]))
        check("Пр. страд. прош. = зака́занный", p["participles"]["past_pass"] == [f"зака{S}занный"], str(p["participles"]["past_pass"]))
        check("pasangan aspek = зака́зывать", p.get("partners") == [f"зака{S}зывать"], str(p.get("partners")))

    print("Homonim: есть")
    rows = lookup_ru(db, "есть")
    eat, be = find(rows, "есть", "VERB"), find(rows, "быть", "VERB")
    check("есть -> есть [makan] DAN быть [ada]", bool(eat) and bool(be), str([(r["lemma_clean"], r["pos"]) for r in rows]))
    if eat:
        p = json.loads(eat["paradigm_json"])
        check("есть: lemma_stressed = е́сть", eat["lemma_stressed"] == f"е{S}сть", eat["lemma_stressed"])
        check("есть: наст. = е́м, е́шь, е́ст, еди́м, еди́те, едя́т",
              [p["present"][k][0] for k in ("sg1", "sg2", "sg3", "pl1", "pl2", "pl3")]
              == [f"е{S}м", f"е{S}шь", f"е{S}ст", f"еди{S}м", f"еди{S}те", f"едя{S}т"])
        check("есть: bentuk jarang ditandai *едя́, *едо́мый",
              p["participles"]["pres_adv"] == [f"*едя{S}"] and p["participles"]["pres_pass"] == [f"*едо{S}мый"],
              f'{p["participles"]["pres_adv"]} {p["participles"]["pres_pass"]}')
        check("есть: terjemahan 'makan'", "makan" in eat["translations_id"], eat["translations_id"])
    if be:
        p = json.loads(be["paradigm_json"])
        check("быть: punya tabel lengkap (3 лица + partisip + catatan kaki)",
              len(p["full_table"]["persons"]) == 3 and len(p["full_table"]["participles"]) >= 5 and len(p["full_table"]["footnotes"]) == 9)
        check("быть: terjemahan 'ada'", "ada" in be["translations_id"].split(", "), be["translations_id"])
    for text, lemma, pos in (("ел", "есть", "VERB"), ("ели", "есть", "VERB"), ("ели", "ель", "NOUN"),
                             ("будучи", "быть", "VERB"), ("бывший", "быть", "VERB"), ("был", "быть", "VERB"), ("суть", "быть", "VERB")):
        rows = lookup_ru(db, text)
        row = find(rows, lemma, pos)
        check(f"{text} -> {lemma} [{pos}]", row is not None, str([(r["lemma_clean"], r["pos"], r["gram_info"]) for r in rows]))

    print("Normalisasi ё/е & kata sifat")
    check("елка -> ёлка", find(lookup_ru(db, "елка"), "ёлка", "NOUN") is not None)
    check("ещё: шел -> идти", find(lookup_ru(db, "шел"), "идти", "VERB") is not None)
    row = find(lookup_ru(db, "новую"), "новый", "ADJ")
    check("новую -> новый (Вин., жен.)", bool(row) and "жен." in row["gram_info"], str(row["gram_info"]) if row else "")
    if row:
        p = json.loads(row["paradigm_json"])
        check("новый: 7 baris kasus + bentuk pendek + komparatif",
              [r["case"] for r in p["rows"]] == ["nom", "gen", "dat", "acc_inan", "acc_anim", "ins", "prep"]
              and bool(p.get("short")) and bool(p.get("comparative")))
    check("детей -> ребёнок", find(lookup_ru(db, "детей"), "ребёнок", "NOUN") is not None)
    check("меня -> я", find(lookup_ru(db, "меня"), "я", "PRON") is not None)
    check("этого -> этот", find(lookup_ru(db, "этого"), "этот", "PRON") is not None)
    check("кофе (tak berdeklinasi)", find(lookup_ru(db, "кофе"), "кофе", "NOUN") is not None)
    check("звучит -> звучать (tabel dipinjam)", find(lookup_ru(db, "звучит"), "звучать", "VERB") is not None)

    print("Pencarian Indonesia")
    for word, lemma, pos in (("mobil", "машина", "NOUN"), ("makan", "есть", "VERB"), ("memesan", "заказать", "VERB"),
                             ("ada", "быть", "VERB"), ("rumah", "дом", "NOUN"), ("baru", "новый", "ADJ"), ("saya", "я", "PRON")):
        rows = lookup_id(db, word)
        row = find(rows, lemma, pos)
        top = [r["lemma_clean"] for r in rows[:6]]
        check(f"{word} -> {lemma}", bool(row) and bool(row["paradigm_json"]), f"teratas: {top}")
    rows = lookup_id(db, "makan")
    check("makan: есть berada di urutan teratas (prioritas 0)", bool(rows) and rows[0]["priority"] == 0
          and "есть" in [r["lemma_clean"] for r in rows if r["priority"] == 0], str([(r["lemma_clean"], r["priority"]) for r in rows[:8]]))

    print("Homonim berejaan sama: terjemahan per arti")
    rows = db.execute("SELECT aspect, translations_id FROM lemmas WHERE lemma_clean='покупать' ORDER BY freq_rank").fetchall()
    check("покупать: НСВ = membeli, СВ = memandikan (bukan membeli)",
          len(rows) == 2 and rows[0][1] == "membeli" and "membeli" not in rows[1][1] and "memandikan" in rows[1][1],
          str([tuple(r) for r in rows]))
    rows = db.execute("SELECT translations_id FROM lemmas WHERE lemma_clean='мусор' ORDER BY freq_rank").fetchall()
    check("мусор: arti utama (sampah) tampil pertama", bool(rows) and rows[0][0] == "sampah", str([r[0] for r in rows]))
    dup = db.execute(
        "SELECT lemma_clean, pos, translations_id FROM lemmas WHERE translations_id != '' "
        "GROUP BY lemma_norm, pos, translations_id HAVING COUNT(*) > 1").fetchall()
    allowed = {"двигать", "засыпать", "нарезать", "насыпать", "просыпаться", "разрезать", "цветок", "шофёр", "шофер"}
    unexpected = [tuple(r) for r in dup if r[0] not in allowed]
    check("tidak ada terjemahan yang tertempel ganda pada homonim", not unexpected, str(unexpected))

    print(f"\nRingkasan:{meta.get('lemma_count')} lema, {meta.get('form_count')} bentuk kata, "
          f"{n_translated} berterjemahan Indonesia, berkas {DB.stat().st_size / 1e6:.1f} MB")
    if failures:
        print(f"{len(failures)} pemeriksaan GAGAL: " + "; ".join(failures))
        return 1
    print("Semua pemeriksaan lulus.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
