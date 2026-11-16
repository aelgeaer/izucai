"""Normalisasi teks Rusia: tanda tekan, ё/е, huruf kecil.

Aturan yang sama diimplementasikan ulang di sisi Android (TextNormalizer.kt);
keduanya harus tetap sinkron agar pencarian cocok dengan kolom `form_clean`.
"""
from __future__ import annotations

import re
import unicodedata

ACUTE = "́"
GRAVE = "̀"
VOWELS = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
SUPERSCRIPTS = "⁰¹²³⁴⁵⁶⁷⁸⁹"

_STRESS_RE = re.compile("[̀́́'’`´]")
_PRE_REFORM_RE = re.compile(r"[ѣѢіІѳѲѵѴ]|ъ(?=$|[\s,;/)-])")
_CYRILLIC_WORD_RE = re.compile(r"^[а-яё\-]+$")
_SPACES_RE = re.compile(r"\s+")


def strip_stress(text: str) -> str:
    """Buang tanda tekan (acute/grave/apostrof), pertahankan ё."""
    text = unicodedata.normalize("NFC", text)
    # NFC dapat menggabungkan huruf + aksen menjadi karakter tunggal untuk beberapa huruf Latin,
    # tetapi huruf Sirilik + U+0301 tetap terpisah, sehingga cukup membuang tanda gabungnya.
    return _STRESS_RE.sub("", text)


def normalize_search(text: str) -> str:
    """Bentuk kunci pencarian: huruf kecil, tanpa tanda tekan, ё -> е, spasi dirapikan."""
    text = strip_stress(text).lower().replace("ё", "е")
    return _SPACES_RE.sub(" ", text).strip()


def lemma_clean(text: str) -> str:
    """Huruf kecil tanpa tanda tekan (ё dipertahankan)."""
    return _SPACES_RE.sub(" ", strip_stress(text).lower()).strip()


def is_pre_reform(text: str) -> bool:
    """True bila mengandung ejaan pra-1918 (ѣ, і, ъ di akhir kata, dst.)."""
    return bool(_PRE_REFORM_RE.search(text))


def is_plain_cyrillic(text: str) -> bool:
    return bool(_CYRILLIC_WORD_RE.match(text))


def count_vowels(word: str) -> int:
    return sum(1 for ch in word if ch in VOWELS)


def has_stress(word: str) -> bool:
    return ACUTE in word or "ё" in word or "Ё" in word


def clean_form(text: str) -> str:
    """Rapikan satu bentuk kata dari Wiktionary: buang catatan kaki, tanda bintang, aksen sekunder."""
    text = unicodedata.normalize("NFC", text)
    text = text.replace(GRAVE, "")
    text = "".join(ch for ch in text if ch not in SUPERSCRIPTS)
    text = text.replace("△", "").replace("*", "").replace(" ", " ")
    return _SPACES_RE.sub(" ", text).strip(" ,;")


def ensure_stress(form: str) -> str:
    """Beri tanda tekan pada kata bersuku satu yang belum bertanda (ел -> е́л).

    Kata dengan ё sudah jelas tekanannya dan dibiarkan apa adanya.
    """
    out = []
    for word in form.split(" "):
        if word and not has_stress(word) and count_vowels(word) == 1 and len(word) > 1:
            for i, ch in enumerate(word):
                if ch in VOWELS:
                    word = word[: i + 1] + ACUTE + word[i + 1 :]
                    break
        out.append(word)
    return " ".join(out)
