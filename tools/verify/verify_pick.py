#!/usr/bin/env python3
"""
Number games (LOTTO, KENO, any N / k / draw) end-to-end against a running WheelLab server, with an
independent brute force here (itertools):

  - count == build total == lines enumerated here; built lines == enumerated lines (per entry, in order)
  - check: hit histogram and total prize == counting line by line
  - check-lines: exactly the lines with >= / == h hits
  - wheels keep their guarantee (every G-subset of the pool is inside some line)
  - position: the rank of a line is its index in lexicographic order

Usage: python3 tools/verify/verify_pick.py [--url http://localhost:8090] [--rounds 50]
"""
import argparse
import itertools
import json
import math
import random
import sys
import urllib.error
import urllib.request


def call(base, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data, {"Content-Type": "application/json"})
    try:
        return json.load(urllib.request.urlopen(req, timeout=300))
    except urllib.error.HTTPError as e:
        return {"__err": json.load(e).get("error")}


def all_lines(base, id_, total):
    out = []
    for start in range(0, total, 1000):
        out += call(base, f"/api/lines?id={id_}&start={start}&size=1000")["lines"]
    return [[int(x) for x in ln] for ln in out]


def entry_lines(e, k):
    t = e["type"]
    if t in ("single", "system"):
        return [list(c) for c in itertools.combinations(sorted(e["numbers"]), k)]
    if t == "groups":
        parts = [list(itertools.combinations(sorted(g), n)) for g, n in zip(e["groups"], e["take"])]
        return [sorted(x for p in combo for x in p) for combo in itertools.product(*parts)]
    raise ValueError(t)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8090")
    ap.add_argument("--rounds", type=int, default=50)
    args = ap.parse_args()
    base = args.url.rstrip("/")
    rnd = random.Random(424242)
    ok = bad = 0

    def check(cond, msg):
        nonlocal ok, bad
        if cond:
            ok += 1
        else:
            bad += 1
            print("FAIL", msg)

    for it in range(args.rounds):
        kind = it % 3
        if kind == 0:
            game = {"numbers": 49, "pick": 6, "draw": 6}          # LOTTO
        elif kind == 1:
            k = rnd.randint(1, 8)
            game = {"numbers": 80, "pick": k, "draw": 20}         # KENO
        else:
            n = rnd.randint(8, 40)
            k = rnd.randint(2, 5)
            game = {"numbers": n, "pick": k, "draw": rnd.randint(k, min(n, k + 6))}
        n, k = game["numbers"], game["pick"]
        nums = list(range(1, n + 1))
        entries = []
        for _ in range(rnd.randint(1, 4)):
            t = rnd.choice(["single", "system", "groups", "wheel"])
            if t == "single":
                entries.append({"type": t, "numbers": rnd.sample(nums, k)})
            elif t == "system":
                entries.append({"type": t, "numbers": rnd.sample(nums, min(n, k + rnd.randint(0, 3)))})
            elif t == "wheel":
                v = min(n, k + rnd.randint(1, 5))
                entries.append({"type": t, "numbers": rnd.sample(nums, v), "guarantee": rnd.randint(1, min(k, 4))})
            else:
                pool = rnd.sample(nums, n)
                groups, take, left, at = [], [], k, 0
                while left:
                    x = rnd.randint(1, left)
                    size = min(x + rnd.randint(0, 2), n - at - (left - x))
                    groups.append(pool[at:at + size])
                    take.append(x)
                    at += size
                    left -= x
                entries.append({"type": t, "groups": groups, "take": take})
        body = {"game": game, "entries": entries}

        c = call(base, "/api/pick/count", body)
        if "__err" in c:
            check(False, f"round {it}: {c['__err']}\n{json.dumps(body)}")
            continue
        b = call(base, "/api/pick/build", body)
        got = all_lines(base, b["id"], b["total"])
        expected, at, same = [], 0, True
        for e, per in zip(entries, b["perEntry"]):
            block = got[at:at + per]
            at += per
            if e["type"] == "wheel":
                ok_wheel = all(sorted(ln) == ln and len(ln) == k and set(ln) <= set(e["numbers"]) for ln in block)
                ok_wheel &= len(set(map(tuple, block))) == len(block)
                for s in itertools.combinations(e["numbers"], e["guarantee"]):
                    ok_wheel &= any(set(s) <= set(ln) for ln in block)
                check(ok_wheel, f"round {it}: wheel guarantee {e}")
                expected += block
            else:
                want = entry_lines(e, k)
                same &= sorted(block) == sorted(want)
                expected += want
        check(same and c["total"] == len(expected) == b["total"], f"round {it}: lines/count {c['total']} {b['total']} {len(expected)}")

        for _ in range(3):
            drawn = rnd.sample(nums, game["draw"])
            hist = [0] * (k + 1)
            for ln in expected:
                hist[len(set(ln) & set(drawn))] += 1
            prizes = [rnd.choice([None, 0, 1, 2.5, 10, 1000]) for _ in range(k + 1)]
            r = call(base, "/api/pick/check", {**body, "drawn": drawn, "payouts": prizes})
            payout = sum((p or 0) * hist[h] for h, p in enumerate(prizes))
            check(r.get("histogram") == hist and math.isclose(float(r.get("payout", "nan")), payout, rel_tol=1e-12, abs_tol=1e-9),
                  f"round {it}: check {r} vs {hist} payout {payout}")
            h = rnd.randint(0, k)
            exact = rnd.random() < 0.5
            want = [ln for ln in expected if (len(set(ln) & set(drawn)) == h if exact else len(set(ln) & set(drawn)) >= h)]
            cl = call(base, "/api/pick/check-lines", {**body, "drawn": drawn, "minHits": h, "exact": exact})
            if "__err" in cl:
                check("More than" in cl["__err"], f"round {it}: check-lines {cl}")
                continue
            check(sorted(all_lines(base, cl["id"], cl["total"])) == sorted(want), f"round {it}: check-lines h={h}")

        # position: in a small pool, every line's rank is its index in lexicographic order
        pool = sorted(rnd.sample(nums, min(n, k + 3)))
        combos = list(itertools.combinations(pool, k))
        pick = rnd.sample(range(len(combos)), min(8, len(combos)))
        p = call(base, "/api/pick/position", {"game": game, "pool": pool, "lines": [" ".join(map(str, combos[i])) for i in pick]})
        check([row.get("rank") for row in p.get("rows", [])] == [i + 1 for i in pick] and p.get("total") == len(combos), f"round {it}: position {p}")

    p = call(base, "/api/pick/position", {"game": {"numbers": 49, "pick": 6, "draw": 6}, "lines": ["1 2 3 4 5 6", "44 45 46 47 48 49", "1 2 3"]})
    rows = p.get("rows", [])
    check(p.get("total") == 13983816 and rows[0].get("rank") == 1 and rows[1].get("rank") == 13983816 and "error" in rows[2], f"position 6/49: {p}")
    for body, fragment in [
        ({"game": {"numbers": 49, "pick": 6, "draw": 6}, "entries": [{"type": "single", "numbers": [1, 2, 3]}]}, "exactly 6"),
        ({"game": {"numbers": 49, "pick": 6, "draw": 6}, "entries": [{"type": "system", "numbers": [1, 2, 3, 4, 5, 50]}]}, "not a number"),
        ({"game": {"numbers": 49, "pick": 6, "draw": 6}, "entries": [{"type": "groups", "groups": [[1, 2, 3], [3, 4, 5, 6]], "take": [2, 4]}]}, "two groups"),
        ({"game": {"numbers": 49, "pick": 6, "draw": 6}, "entries": [{"type": "wheel", "numbers": list(range(1, 50)), "guarantee": 6}]}, "combinations to cover"),
    ]:
        r = call(base, "/api/pick/count", body)
        check("__err" in r and fragment in r["__err"], f"error message: {r}")

    print(f"{ok} OK, {bad} FAIL")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
