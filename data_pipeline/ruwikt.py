"""Sumber pelengkap: Wiktionary bahasa Rusia (ru.wiktionary.org, ekstraksi Kaikki/wiktextract).

Dipakai HANYA untuk kata di kamus kurasi yang tidak ada di Wiktionary bahasa Inggris — kebanyakan
kosakata kampus/IT modern (зачётка, пересдача, хакатон, фреймворк, конспектировать, ...).

Entri ru.wiktionary diubah ke bentuk yang dipahami kaikki_parser:
  - setiap bentuk diberi "source" (declension / conjugation);
  - bentuk yang sama dengan judul halaman tidak dicantumkan wiktextract -> disisipkan kembali;
  - lampau jamak ditandai "masculine+feminine+neuter" -> "plural";
  - imperatif tanpa bilangan -> singular/plural dari akhiran «-те»; imperatif orang ke-1 (давайте) dibuang;
  - aspek "perfect" -> "perfective";
  - varian «тёпел // тёпл^△» dirapikan.
"""
from __future__ import annotations

import gzip
import json
from collections.abc import Callable, Iterable
from pathlib import Path

from textnorm import ACUTE, normalize_search, strip_stress

RUWIKT_POS = {"noun": "NOUN", "verb": "VERB", "adj": "ADJ"}
_HEAD_TAGS = {"masculine", "feminine", "neuter", "animate", "inanimate", "indeclinable"}


def _clean(form: str) -> str:
    return form.replace("//", "/").replace("^", "").replace("△", "").strip()


def _stressed_infinitive(word: str, forms: list[dict]) -> str:
    """Infinitif bertanda tekan diturunkan dari lampau maskulin: конспекти́ровал -> конспекти́ровать."""
    past_m = [f["form"] for f in forms if {"past", "masculine"} <= set(f["tags"]) and "plural" not in f["tags"]]
    for form in past_m:
        bare = strip_stress(form)
        if word.endswith("ться") and bare == word[:-4] + "лся":
            return form[:-3] + "ться"
        if word.endswith("ть") and bare == word[:-2] + "л":
            return form[:-1] + "ть"
    return word


def adapt(entry: dict) -> dict | None:
    pos = entry.get("pos")
    if pos not in RUWIKT_POS:
        return None
    word = entry["word"]
    source = "conjugation" if pos == "verb" else "declension"
    entry_tags = set(entry.get("tags") or ())
    forms: list[dict] = []
    for f in entry.get("forms", ()):
        raw = _clean(f.get("form") or "")
        tags = set(f.get("tags") or ())
        if not raw or not tags:
            continue
        if pos == "verb":
            if "participle" not in tags and tags & {"nominative", "genitive", "dative", "accusative",
                                                     "instrumental", "prepositional"}:
                continue  # tabel deklinasi partisip -> tidak dipakai
            if "imperative" in tags:
                if "first-person" in tags:
                    continue
                if not tags & {"singular", "plural"}:
                    tags.add("plural" if strip_stress(raw).endswith("те") else "singular")
            if "past" in tags and {"masculine", "feminine", "neuter"} <= tags:
                tags = {"past", "plural"}
        forms.append({"form": raw, "tags": sorted(tags), "source": source})

    aspect = {"perfective"} if "perfect" in entry_tags or "perfective" in entry_tags else (
        {"imperfective"} if "imperfective" in entry_tags else set())
    head = sorted(entry_tags & _HEAD_TAGS)

    if pos == "verb":
        canonical = _stressed_infinitive(word, forms)
        forms.append({"form": canonical, "tags": ["infinitive", *aspect], "source": source})
        head += sorted(aspect)
    elif pos == "noun":
        has_sg = any("singular" in f["tags"] for f in forms)
        nom_sg = next((f["form"] for f in forms if {"nominative", "singular"} <= set(f["tags"])), None)
        if has_sg and nom_sg is None:
            nom_sg = word
            forms.append({"form": word, "tags": ["nominative", "singular"], "source": source})
        if not has_sg:
            head.append("plural")
            nom_pl = next((f["form"] for f in forms if {"nominative", "plural"} <= set(f["tags"])), None)
            canonical = nom_pl or word
        else:
            canonical = nom_sg
        if not forms and "indeclinable" in entry_tags:
            canonical = word
    else:  # adj
        nom_m = next((f["form"] for f in forms
                      if {"nominative", "masculine", "singular"} <= set(f["tags"])), None)
        if nom_m is None:
            nom_m = word
            forms.append({"form": word, "tags": ["masculine", "nominative", "singular"], "source": source})
            forms.append({"form": word, "tags": ["accusative", "inanimate", "masculine", "singular"], "source": source})
        canonical = nom_m
    if strip_stress(canonical) != word and ACUTE not in canonical:
        canonical = word
    forms.insert(0, {"form": canonical, "tags": ["canonical", *head]})
    return {"word": word, "pos": pos, "lang_code": "ru", "forms": forms, "senses": []}


def scan(path: Path, wanted: dict[str, set[str]]) -> Iterable[tuple[str, dict]]:
    """Hasilkan (kelas kata, entri teradaptasi) untuk kata-kata yang dicari {norm: {POS}}."""
    words = {w for w in wanted}
    with gzip.open(path, "rt", encoding="utf-8") as fh:
        for line in fh:
            if '"lang_code": "ru"' not in line:
                continue
            i = line.find('"word": "')
            if i < 0:
                continue
            word = line[i + 9: line.find('"', i + 9)]
            norm = normalize_search(word)
            if norm not in words:
                continue
            entry = json.loads(line)
            # Kunci "word" pertama di baris bisa milik daftar kata turunan, bukan entri ini.
            norm = normalize_search(entry.get("word") or "")
            pos = RUWIKT_POS.get(entry.get("pos"))
            if norm not in words or pos not in wanted[norm]:
                continue
            adapted = adapt(entry)
            if adapted:
                yield pos, adapted


def supplement(path: Path, wanted: dict[str, set[str]], consider: Callable[[dict, str], bool]) -> int:
    added = 0
    for pos, entry in scan(path, wanted):
        if consider(entry, pos):
            added += 1
    return added
