"""Compare a regenerated voc.db against the original app database.

Matches rows by nearest ingress time and reports mismatches in the
sign/aspect/planet codes and time deltas. Uses only the Python stdlib.
"""
import sqlite3
import sys

old_path, new_path = sys.argv[1], sys.argv[2]
old = sqlite3.connect(old_path).execute("SELECT * FROM voc ORDER BY ingress").fetchall()
new = sqlite3.connect(new_path).execute("SELECT * FROM voc ORDER BY ingress").fetchall()

print(f"old: {len(old)} rows, new: {len(new)} rows")

new_by_ingress = {r[1]: r for r in new}
unmatched = 0
field_mismatch = []
ing_deltas = []
asp_deltas = []

for o in old:
    # find nearest new ingress within 10 minutes
    cand = min(new, key=lambda n: abs(n[1] - o[1]))
    if abs(cand[1] - o[1]) > 600:
        unmatched += 1
        print(f"UNMATCHED old row: {o}")
        continue
    ing_deltas.append(cand[1] - o[1])
    asp_deltas.append(cand[4] - o[4])
    if (o[0], o[2], o[3]) != (cand[0], cand[2], cand[3]):
        field_mismatch.append((o, cand))

print(f"unmatched rows: {unmatched}")
print(f"sign/aspect/planet mismatches: {len(field_mismatch)}")
for o, n in field_mismatch[:20]:
    print(f"  old={o}  new={n}")

if ing_deltas:
    print(f"ingress time delta (new-old) s: min={min(ing_deltas)} max={max(ing_deltas)} "
          f"mean={sum(ing_deltas)/len(ing_deltas):.2f}")
    print(f"asptime  time delta (new-old) s: min={min(asp_deltas)} max={max(asp_deltas)} "
          f"mean={sum(asp_deltas)/len(asp_deltas):.2f}")
