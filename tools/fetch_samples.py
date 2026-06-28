#!/usr/bin/env python3
# Copyright (C) 2026 Frédéric France
# SPDX-License-Identifier: GPL-3.0-or-later
"""
Telecharge les echantillons d'instruments depuis VSCO 2 Community Edition (CC0)
et les prepare pour SampledNotePlayer.

Source : depot public https://github.com/sgossner/VSCO-2-CE (WAV bruts, licence
CC0 / domaine public). Aucun compte ni cle d'API requis.

Pour chaque instrument, le script choisit un echantillon par hauteur, le
convertit en OGG/Vorbis mono via ffmpeg et l'ecrit sous :

    app/src/main/assets/samples/<instrument>/<midi>.ogg

ou <midi> est le numero MIDI de la note (Do4 = 60, La4 = 69), exactement ce
qu'attend SampledNotePlayer.

Instruments couverts : piano, violin, flute. L'orgue (pas de table de mapping
fournie en amont) et la guitare (absente de VSCO 2 CE) ne sont pas pris en
charge : l'application retombe alors sur le synthetiseur General MIDI.

Dependances : python3 et ffmpeg.

Exemples :
    tools/fetch_samples.py --dry-run            # liste sans rien telecharger
    tools/fetch_samples.py                       # tous les instruments geres
    tools/fetch_samples.py --instruments violin  # un seul
    tools/fetch_samples.py --force --quality 5   # reconvertit, qualite superieure
"""
from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request

REPO = "sgossner/VSCO-2-CE"
BRANCH = "master"
RAW_BASE = f"https://raw.githubusercontent.com/{REPO}/{BRANCH}/"
TREE_API = f"https://api.github.com/repos/{REPO}/git/trees/{BRANCH}?recursive=1"

# Dossier de sortie, relatif a la racine du depot (deduite via ce fichier).
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT_ROOT = os.path.join(REPO_ROOT, "app", "src", "main", "assets", "samples")

NOTE_PC = {"C": 0, "D": 2, "E": 4, "F": 5, "G": 7, "A": 9, "B": 11}


def note_to_midi(name: str) -> int | None:
    """'A4' -> 69, 'C4' -> 60, 'C#5'/'Db5' geres. None si non reconnu."""
    m = re.fullmatch(r"([A-G])([#b]?)(-?\d+)", name)
    if not m:
        return None
    pc = NOTE_PC[m.group(1)]
    if m.group(2) == "#":
        pc += 1
    elif m.group(2) == "b":
        pc -= 1
    return 12 * (int(m.group(3)) + 1) + pc


# Definitions des instruments. "kind" determine le mode de selection :
#   - "note"  : la note est dans le nom de fichier (regex avec 1 groupe).
#   - "index" : le nom porte un index numerique, traduit via une MappingChart.
INSTRUMENTS = {
    "piano": {
        "kind": "index",
        "dir": "Keys/Upright Piano/",
        "regex": re.compile(r"Player_dyn2_rr1_(\d+)\.wav$"),
        "mapping": "Keys/Upright Piano/MappingChart.txt",
    },
    "violin": {
        "kind": "note",
        "dir": "Strings/Solo Violin/Arco Vib/",
        # _f = forte (timbre clair et present, ideal comme reference).
        "regex": re.compile(r"_([A-G][#b]?-?\d)_f\.wav$"),
    },
    "flute": {
        "kind": "note",
        "dir": "Woodwinds/Flute/susvib/",
        # susvib = note tenue avec vibrato ; on garde la 1re prise (_v1_1).
        "regex": re.compile(r"_([A-G][#b]?-?\d)_v1_1\.wav$"),
    },
}


def fetch_tree() -> list[str]:
    req = urllib.request.Request(TREE_API, headers={"User-Agent": "fetch_samples"})
    with urllib.request.urlopen(req) as resp:
        data = json.load(resp)
    if data.get("truncated"):
        print("  ! Arborescence tronquee par l'API GitHub ; resultats partiels.", file=sys.stderr)
    return [t["path"] for t in data.get("tree", []) if t.get("type") == "blob"]


def fetch_mapping(path: str) -> dict[int, int]:
    """Parse une MappingChart 'index=midi' -> {index: midi}."""
    url = RAW_BASE + urllib.parse.quote(path)
    req = urllib.request.Request(url, headers={"User-Agent": "fetch_samples"})
    mapping: dict[int, int] = {}
    with urllib.request.urlopen(req) as resp:
        for raw in resp.read().decode("utf-8", "replace").splitlines():
            left, sep, right = raw.partition("=")
            if sep and left.strip().isdigit() and right.strip().isdigit():
                mapping[int(left)] = int(right)
    return mapping


def select(name: str, cfg: dict, paths: list[str], lo: int, hi: int) -> dict[int, str]:
    """Retourne {midi: chemin_depot} : un echantillon par hauteur, dans [lo, hi]."""
    files = [p for p in paths if p.startswith(cfg["dir"]) and p.lower().endswith(".wav")]
    chart = fetch_mapping(cfg["mapping"]) if cfg["kind"] == "index" else {}
    chosen: dict[int, str] = {}
    for p in sorted(files):
        fname = p.rsplit("/", 1)[-1]
        m = cfg["regex"].search(fname)
        if not m:
            continue
        if cfg["kind"] == "note":
            midi = note_to_midi(m.group(1))
        else:
            midi = chart.get(int(m.group(1)))
        if midi is None or not (lo <= midi <= hi):
            continue
        chosen.setdefault(midi, p)  # 1er fichier rencontre pour cette hauteur
    return chosen


def convert(repo_path: str, dst: str, quality: int) -> None:
    url = RAW_BASE + urllib.parse.quote(repo_path)
    req = urllib.request.Request(url, headers={"User-Agent": "fetch_samples"})
    with urllib.request.urlopen(req) as resp, \
            tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        shutil.copyfileobj(resp, tmp)
        tmp_path = tmp.name
    try:
        subprocess.run(
            ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp_path,
             "-ac", "1", "-c:a", "libvorbis", "-q:a", str(quality), dst],
            check=True,
        )
    finally:
        os.unlink(tmp_path)


def main() -> int:
    ap = argparse.ArgumentParser(description="Telecharge les echantillons VSCO 2 (CC0) en OGG.")
    ap.add_argument("--instruments", default="all",
                    help="liste separee par des virgules (defaut: all) parmi: "
                         + ", ".join(INSTRUMENTS))
    ap.add_argument("--quality", type=int, default=4, help="qualite OGG libvorbis 0-10 (defaut 4)")
    ap.add_argument("--range", default="33,96", help="plage MIDI min,max (defaut 33,96)")
    ap.add_argument("--force", action="store_true", help="reconvertit meme si l'OGG existe")
    ap.add_argument("--dry-run", action="store_true", help="liste sans telecharger")
    args = ap.parse_args()

    if not args.dry_run and not shutil.which("ffmpeg"):
        print("Erreur : ffmpeg introuvable dans le PATH.", file=sys.stderr)
        return 1

    wanted = list(INSTRUMENTS) if args.instruments == "all" \
        else [s.strip() for s in args.instruments.split(",") if s.strip()]
    unknown = [w for w in wanted if w not in INSTRUMENTS]
    if unknown:
        print(f"Instrument(s) inconnu(s) : {', '.join(unknown)}", file=sys.stderr)
        print(f"Disponibles : {', '.join(INSTRUMENTS)}", file=sys.stderr)
        return 1

    lo, hi = (int(x) for x in args.range.split(","))
    print(f"Source : github.com/{REPO} (CC0)")
    print("Recuperation de l'arborescence...")
    paths = fetch_tree()

    total = 0
    for name in wanted:
        cfg = INSTRUMENTS[name]
        chosen = select(name, cfg, paths, lo, hi)
        if not chosen:
            print(f"\n[{name}] aucun echantillon trouve (structure du depot modifiee ?)")
            continue
        out_dir = os.path.join(OUT_ROOT, name)
        if not args.dry_run:
            os.makedirs(out_dir, exist_ok=True)
        print(f"\n[{name}] {len(chosen)} hauteurs -> {os.path.relpath(out_dir, REPO_ROOT)}/")
        for midi in sorted(chosen):
            dst = os.path.join(out_dir, f"{midi}.ogg")
            src = chosen[midi]
            if args.dry_run:
                print(f"  MIDI {midi:3d}  <-  {src.rsplit('/', 1)[-1]}")
                continue
            if os.path.exists(dst) and not args.force:
                print(f"  MIDI {midi:3d}  (existe, ignore)")
                continue
            try:
                convert(src, dst, args.quality)
                print(f"  MIDI {midi:3d}  <-  {src.rsplit('/', 1)[-1]}")
                total += 1
            except (subprocess.CalledProcessError, OSError) as e:
                print(f"  MIDI {midi:3d}  ECHEC ({e})", file=sys.stderr)

    if args.dry_run:
        print("\n(dry-run : rien n'a ete telecharge)")
    else:
        print(f"\nTermine : {total} fichier(s) OGG ecrit(s) sous {os.path.relpath(OUT_ROOT, REPO_ROOT)}/")
        print("Pense a verifier l'ecoute, puis commit + nouveau tag pour publier la release.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
