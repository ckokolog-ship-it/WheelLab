#!/usr/bin/env python3
"""
Number games (LOTTO, KENO, any N / k / draw) end-to-end against a running WheelLab server, with an
independent brute force here (itertools):

  - count == build total == lines enumerated here; built lines == enumerated lines (per entry, in order)
  - check: hit histogram and total prize == counting line by line
  - check-lines: exactly the lines with >= / == h hits
  - wheels keep their guarantee (every G-subset of the pool is inside some line)
  - position: the rank of a line is its index in lexicographic order
  - your own games (numbers from any min, a bonus pool): lines with +bonus, the main x bonus hit grid,
    prizes per (main, bonus) hits, odd/even and last-digit analyses, number frequencies, and the game
    language (text -> game -> the same text)

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


def bonus_rounds(base, rnd, check, rounds):
    for it in range(rounds):
        lo = rnd.choice([0, 1, 5])
        k = rnd.randint(2, 4)
        hi = lo + rnd.randint(k + 5, 25)
        bp = rnd.randint(1, 2)
        bmax = rnd.randint(bp + 2, 9)
        game = {"title": f"Test {it}", "min": lo, "max": hi, "pick": k, "draw": k,
                "bonus": {"label": "Star", "min": 1, "max": bmax, "pick": bp, "draw": bp}}
        nums, bnums = list(range(lo, hi + 1)), list(range(1, bmax + 1))
        entries = []
        for _ in range(rnd.randint(1, 3)):
            t = rnd.choice(["single", "system", "groups", "wheel"])
            if t == "single":
                e = {"type": t, "numbers": rnd.sample(nums, k)}
            elif t == "system":
                e = {"type": t, "numbers": rnd.sample(nums, k + rnd.randint(0, 3))}
            elif t == "wheel":
                e = {"type": t, "numbers": rnd.sample(nums, k + rnd.randint(1, 4)), "guarantee": rnd.randint(1, k)}
            else:
                a = rnd.randint(1, k - 1)
                pool = rnd.sample(nums, k + 4)
                e = {"type": t, "groups": [pool[:a + 2], pool[a + 2:]], "take": [a, k - a]}
            e["bonus"] = rnd.sample(bnums, bp + (rnd.randint(0, bmax - bp) if t == "system" else 0))
            entries.append(e)
        body = {"game": game, "entries": entries}
        b = call(base, "/api/pick/build", body)
        if "__err" in b:
            check(False, f"bonus {it}: {b['__err']}\n{json.dumps(body)}")
            continue
        raw = []
        for start in range(0, b["total"], 1000):
            raw += call(base, f"/api/lines?id={b['id']}&start={start}&size=1000")["lines"]
        expected, at, same = [], 0, True
        for e, per in zip(entries, b["perEntry"]):
            block = raw[at:at + per]
            at += per
            if e["type"] == "wheel":
                mains = sorted({tuple(int(x) for x in ln if not x.startswith("+")) for ln in block})
            else:
                mains = [tuple(c) for c in entry_lines(e, k)]
            want = [[str(x) for x in m] + ["+" + str(x) for x in bc] for m in mains for bc in itertools.combinations(sorted(e["bonus"]), bp)]
            same &= sorted(block) == sorted(want)
            expected += want
        check(same, f"bonus {it}: lines with bonus")
        drawn, dbonus = rnd.sample(nums, k), rnd.sample(bnums, bp)
        grid = [[0] * (bp + 1) for _ in range(k + 1)]
        freq, bfreq = {}, {}
        for ln in expected:
            h = sum(1 for x in ln if not x.startswith("+") and int(x) in drawn)
            sh = sum(1 for x in ln if x.startswith("+") and int(x[1:]) in dbonus)
            grid[h][sh] += 1
            for x in ln:
                d = bfreq if x.startswith("+") else freq
                v = int(x.lstrip("+"))
                d[v] = d.get(v, 0) + 1
        prizes = [[rnd.choice([None, 0, 3, 100]) for _ in range(bp + 1)] for _ in range(k + 1)]
        r = call(base, "/api/pick/check", {**body, "drawn": drawn, "drawnBonus": dbonus, "payouts": prizes})
        pay = sum((prizes[h][s_] or 0) * grid[h][s_] for h in range(k + 1) for s_ in range(bp + 1))
        check(r.get("grid") == grid and r.get("histogram") == [sum(row) for row in grid] and float(r.get("payout", -1)) == pay, f"bonus {it}: grid {r} vs {grid}")
        f = call(base, "/api/pick/frequency", body)
        check({x["number"]: x["count"] for x in f.get("main", [])} == freq and {x["number"]: x["count"] for x in f.get("bonus", [])} == bfreq, f"bonus {it}: frequency")
        for i, e in enumerate(entries):
            if e["type"] != "system":
                continue
            a = call(base, "/api/pick/analysis", {**body, "entry": i})
            lines_ = [ln for ln in expected[sum(b["perEntry"][:i]):sum(b["perEntry"][:i + 1])]]
            by_odd = [0] * (k + 1)
            for ln in lines_:
                by_odd[sum(1 for x in ln if not x.startswith("+") and int(x) % 2)] += 1
            ok_d = True
            for d in a["lastDigit"]:
                want = [0] * (k + 1)
                for ln in lines_:
                    want[sum(1 for x in ln if not x.startswith("+") and int(x) % 10 == d["digit"])] += 1
                ok_d &= d["byCount"] == want
            check(a["oddEven"]["byOdd"] == by_odd and ok_d and a["total"] == len(lines_), f"bonus {it}: analysis {a}")
        pool, bpool = sorted(rnd.sample(nums, k + 2)), sorted(rnd.sample(bnums, bp + 1))
        combos = [(m, bc) for m in itertools.combinations(pool, k) for bc in itertools.combinations(bpool, bp)]
        pick = rnd.sample(range(len(combos)), min(6, len(combos)))
        texts = [" ".join(map(str, combos[i][0])) + " | " + " ".join(map(str, combos[i][1])) for i in pick]
        p = call(base, "/api/pick/position", {"game": game, "pool": pool, "bonusPool": bpool, "lines": texts})
        check([row.get("rank") for row in p.get("rows", [])] == [i + 1 for i in pick], f"bonus {it}: position {p}")
        prize_keys = {f"{h}+{s_}": prizes[h][s_] for h in range(k + 1) for s_ in range(bp + 1) if prizes[h][s_]}
        t = call(base, "/api/pick/text", {**body, "prizes": prize_keys})
        back = call(base, "/api/pick/parse", {"text": t.get("text", "")})
        again = call(base, "/api/pick/text", back)
        check(t.get("text") and again.get("text") == t["text"] and back.get("entries") and len(back["entries"]) == len(entries), f"bonus {it}: text round trip {t} {back}")


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

    bonus_rounds(base, rnd, check, max(10, args.rounds // 2))
    for text, fragment in [('game "x" numbers 1-50 pick 5\nentry single 1 2 3', "Line 2"),
                           ('game "x" numbers 1-50 pick 5\nfoo', "not understood"),
                           ('entry single 1 2 3 4 5', "game line comes first"),
                           ('game "x" numbers 1-50 pick 5\nprize 5+1 = 10', "no bonus")]:
        r = call(base, "/api/pick/parse", {"text": text})
        check("__err" in r and fragment in r["__err"], f"language error {text!r}: {r}")
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
