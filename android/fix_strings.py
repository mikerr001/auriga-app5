#!/usr/bin/env python3
"""Fix Unicode ellipsis (U+2026) in all locale strings.xml files.
Must be run from the android/ directory or the repo root (auto-detects).
"""
import os
import sys

script_dir = os.path.dirname(os.path.abspath(__file__))
res_dir = os.path.join(script_dir, "app", "src", "main", "res")

if not os.path.isdir(res_dir):
    print(f"ERROR: res dir not found at {res_dir}", file=sys.stderr)
    sys.exit(1)

patched = 0
for entry in os.scandir(res_dir):
    if not (entry.is_dir() and entry.name.startswith("values")):
        continue
    p = os.path.join(entry.path, "strings.xml")
    if not os.path.isfile(p):
        continue
    with open(p, "r", encoding="utf-8") as f:
        src = f.read()
    if "\u2026" not in src:
        continue
    with open(p, "w", encoding="utf-8") as f:
        f.write(src.replace("\u2026", "&#8230;"))
    print(f"  Fixed {entry.name}/strings.xml")
    patched += 1

print(f"Done — {patched} file(s) patched.")
