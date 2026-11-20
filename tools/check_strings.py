#!/usr/bin/env python3
"""检查 strings.xml 是否有重复的 name（AAPT 会直接报错）。"""
import io
import re
import sys

bad = 0
for p in sys.argv[1:]:
    names = []
    for line in io.open(p, encoding="utf-8"):
        m = re.search(r'<(?:string|plurals|string-array)\s+name="([^"]+)"', line)
        if m:
            names.append(m.group(1))
    seen = {}
    for n in names:
        seen[n] = seen.get(n, 0) + 1
    dups = {k: v for k, v in seen.items() if v > 1}
    if dups:
        bad += 1
        print(f"DUPLICATE {p}")
        for k, v in dups.items():
            print(f"   {k} x{v}")
    else:
        print(f"OK        {p}  ({len(names)} entries)")
sys.exit(1 if bad else 0)
