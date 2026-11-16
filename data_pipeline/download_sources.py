"""Unduh sumber data morfologi (sekali saja, lalu di-cache di data_pipeline/sources/).

Sumber: Kaikki.org -- hasil ekstraksi Wiktionary (en.wiktionary.org) untuk bahasa Rusia.
Berisi tabel deklinasi/konjugasi lengkap dengan tanda tekan (ударение).
Lisensi data: CC BY-SA 4.0 / GFDL (Wiktionary).

Jalankan:  python data_pipeline/download_sources.py
"""
from __future__ import annotations

import http.client
import sys
import time
import urllib.request
from pathlib import Path

KAIKKI_URL = "https://kaikki.org/dictionary/Russian/kaikki.org-dictionary-Russian.jsonl.gz"
SOURCES_DIR = Path(__file__).resolve().parent / "sources" / "kaikki"
KAIKKI_FILE = SOURCES_DIR / "kaikki-russian.jsonl.gz"

# Daftar frekuensi bentuk kata (korpus OpenSubtitles 2018, proyek FrequencyWords, CC BY-SA 4.0).
# Dipakai untuk mengurutkan lema dari yang paling sering dipakai.
FREQ_URL = "https://raw.githubusercontent.com/hermitdave/FrequencyWords/master/content/2018/ru/ru_full.txt"
FREQ_FILE = Path(__file__).resolve().parent / "sources" / "freq" / "ru_full.txt"

# Ekstraksi Wiktionary bahasa Rusia (ru.wiktionary.org) — melengkapi kata modern/teknis yang belum ada
# di Wiktionary bahasa Inggris (зачётка, пересдача, хакатон, фреймворк, ...). CC BY-SA 4.0.
RUWIKT_URL = "https://kaikki.org/ruwiktionary/raw-wiktextract-data.jsonl.gz"
RUWIKT_FILE = Path(__file__).resolve().parent / "sources" / "ruwiktionary" / "ruwiktionary-raw.jsonl.gz"


def _fetch_range(url: str, tmp: Path, start: int) -> tuple[int, int]:
    """Unduh mulai dari byte `start` (melanjutkan file .part). Mengembalikan (terunduh, total)."""
    headers = {"User-Agent": "Mozilla/5.0 (generic-client)"}
    if start:
        headers["Range"] = f"bytes={start}-"
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=60) as resp:
        if start and resp.status != 206:
            start = 0  # server tidak mendukung Range -> mulai ulang dari awal
        total = start + int(resp.headers.get("Content-Length") or 0)
        done = start
        next_report = done
        with open(tmp, "ab" if start else "wb") as out:
            try:
                while True:
                    chunk = resp.read(1 << 18)
                    if not chunk:
                        break
                    out.write(chunk)
                    done += len(chunk)
                    if done >= next_report:
                        print(f"  {done / 1e6:7.1f} MB  ({done * 100 // max(total, 1)}%)", flush=True)
                        next_report += 10 * (1 << 20)
            except (OSError, http.client.HTTPException) as exc:
                print(f"  koneksi terputus di {done / 1e6:.1f} MB: {exc}", flush=True)
    return done, total


def download(url: str, dest: Path, max_attempts: int = 60) -> None:
    dest.parent.mkdir(parents=True, exist_ok=True)
    tmp = dest.with_suffix(dest.suffix + ".part")
    for attempt in range(1, max_attempts + 1):
        start = tmp.stat().st_size if tmp.exists() else 0
        try:
            done, total = _fetch_range(url, tmp, start)
        except (OSError, http.client.HTTPException) as exc:
            print(f"  percobaan {attempt} gagal: {exc}", flush=True)
            time.sleep(3)
            continue
        if total and done >= total:
            tmp.replace(dest)
            return
        time.sleep(2)
    raise RuntimeError(f"Unduhan gagal setelah {max_attempts} percobaan")


def main() -> int:
    for url, dest, min_size in (
        (KAIKKI_URL, KAIKKI_FILE, 50_000_000),
        (FREQ_URL, FREQ_FILE, 10_000_000),
        (RUWIKT_URL, RUWIKT_FILE, 200_000_000),
    ):
        if dest.exists() and dest.stat().st_size > min_size:
            print(f"Sudah ada: {dest} ({dest.stat().st_size / 1e6:.1f} MB)")
            continue
        print(f"Mengunduh {url}")
        download(url, dest)
        print(f"Selesai: {dest} ({dest.stat().st_size / 1e6:.1f} MB)")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")  # konsol Windows: cetak Sirilik dengan benar
    sys.exit(main())
