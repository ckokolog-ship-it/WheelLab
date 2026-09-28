#!/usr/bin/env python3
"""
MatchX end-to-end check against a running WheelLab server, with an independent brute force here
(itertools): random tickets of Full / Errors / Ranges / Covering groups on 1X2, Under/Over and GG/NG
matches, several tickets appended.

  - count == build total == the number of lines enumerated here
  - the built lines (read page by page) are exactly the lines enumerated here, in ticket order
  - check: the hit histogram of every ticket matches counting line by line
  - check-lines: the lines with >= / exactly h hits are the right ones
  - covering groups keep their guarantee

Usage: python3 tools/verify/verify_matchx.py [--url http://localhost:8090] [--rounds 60]
"""
import argparse
import itertools
import json
import random
import sys
import urllib.error
import urllib.request

FAMILIES = [["1", "X", "2"], ["U", "O"], ["GG", "NG"]]


def call(base, path, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request(base + path, data, {"Content-Type": "application/json"})
    try:
        return json.load(urllib.request.urlopen(req, timeout=120))
    except urllib.error.HTTPError as e:
        return {"__err": json.load(e).get("error")}


def all_lines(base, id_, total):
    out = []
    for start in range(0, total, 1000):
        out += call(base, f"/api/lines?id={id_}&start={start}&size=1000")["lines"]
    return out


def random_picks(rnd, size, full_family=False):
    picks = []
    for _ in range(size):
        fam = rnd.choice(FAMILIES)
        picks.append(rnd.sample(fam, len(fam) if full_family else rnd.randint(1, len(fam))))
    return picks


def group_lines(g):
    """Every line of a group, by brute force (order does not matter here)."""
    picks = g["picks"]
    combos = list(itertools.product(*picks))
    if g["type"] == "full":
        return [list(c) for c in combos]
    if g["type"] == "errors":
        return [list(c) for c in combos if sum(a != p[0] for a, p in zip(c, picks)) in g["errors"]]
    if g["type"] == "ranges":
        out = []
        for c in combos:
            counts = [0, 0, 0]
            for a, p in zip(c, picks):
                counts[p.index(a)] += 1
            if all(lo <= counts[r] <= hi for r, (lo, hi) in enumerate(g["ranges"])):
                out.append(list(c))
        return out
    raise ValueError(g["type"])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--url", default="http://localhost:8090")
    ap.add_argument("--rounds", type=int, default=60)
    args = ap.parse_args()
    base = args.url.rstrip("/")
    rnd = random.Random(20260928)
    ok = bad = 0

    def check(cond, msg):
        nonlocal ok, bad
        if cond:
            ok += 1
        else:
            bad += 1
            print("FAIL", msg)

    for it in range(args.rounds):
        n = rnd.randint(2, 8)
        tickets, expected, results_family = [], [], [None] * n
        for _ in range(rnd.randint(1, 3)):
            order = list(range(1, n + 1))
            rnd.shuffle(order)
            played = order[: rnd.randint(1, n)]  # some matches may be in no group
            groups, at = [], 0
            while at < len(played):
                size = rnd.randint(1, min(4, len(played) - at))
                ms = sorted(played[at:at + size])
                at += size
                kind = rnd.choice(["full", "errors", "ranges", "covering"])
                g = {"type": kind, "matches": ms, "picks": random_picks(rnd, size, kind == "ranges")}
                if kind == "errors":
                    g["errors"] = sorted(rnd.sample(range(size + 1), rnd.randint(1, size + 1)))
                if kind == "ranges":
                    g["ranges"] = [sorted([rnd.randint(0, size), rnd.randint(0, size)]) for _ in range(3)]
                if kind == "covering":
                    g["radius"], g["guarantee"] = rnd.randint(0, size), rnd.randint(1, size)
                groups.append(g)
            tickets.append({"groups": groups})
        body = {"matches": n, "tickets": tickets}

        # covering lines come from the server -- check their guarantee, then use them as the group's lines
        for t in tickets:
            for g in t["groups"]:
                if g["type"] != "covering":
                    continue
                one = {"matches": n, "tickets": [{"groups": [g]}]}
                b = call(base, "/api/matchx/build", one)
                rows = [[ln[m - 1] for m in g["matches"]] for ln in all_lines(base, b["id"], b["total"])]
                good = True
                for res in itertools.product(*g["picks"]):
                    if sum(a != p[0] for a, p in zip(res, g["picks"])) > g["radius"]:
                        continue
                    good &= max(sum(x == y for x, y in zip(r, res)) for r in rows) >= g["guarantee"]
                check(good and len(set(map(tuple, rows))) == len(rows), f"round {it}: covering guarantee {g}")
                g["_rows"] = rows

        per_ticket = []
        for t in tickets:
            lines = [["-"] * n]
            for g in t["groups"]:
                gl = g.pop("_rows") if "_rows" in g else group_lines(g)
                nxt = []
                for base_line in lines:
                    for row in gl:
                        ln = list(base_line)
                        for m, s in zip(g["matches"], row):
                            ln[m - 1] = s
                        nxt.append(ln)
                lines = nxt
            per_ticket.append(lines)
            expected += lines

        c = call(base, "/api/matchx/count", body)
        if "__err" in c:
            check(False, f"round {it}: {c['__err']}\n{json.dumps(body)}")
            continue
        check(c["total"] == len(expected) and [t["total"] for t in c["tickets"]] == [len(x) for x in per_ticket],
              f"round {it}: count {c['total']} vs {len(expected)}")
        b = call(base, "/api/matchx/build", body)
        got = all_lines(base, b["id"], b["total"])
        start, same = 0, len(got) == len(expected)
        for lines in per_ticket:  # tickets in order, lines within a ticket in any order
            same &= sorted(map(tuple, got[start:start + len(lines)])) == sorted(map(tuple, lines))
            start += len(lines)
        check(same, f"round {it}: built lines")

        for _ in range(3):
            fams = [None] * n
            for t in tickets:
                for g in t["groups"]:
                    for m, p in zip(g["matches"], g["picks"]):
                        fams[m - 1] = fams[m - 1] or next(f for f in FAMILIES if p[0] in f)
            result = [rnd.choice(f or FAMILIES[0]) for f in fams]
            r = call(base, "/api/matchx/check", {**body, "result": result})
            hist = [0] * (n + 1)
            for ln in expected:
                hist[sum(a == b for a, b in zip(ln, result))] += 1
            check(r.get("histogram") == hist and r.get("total") == len(expected), f"round {it}: check {r} vs {hist}")
            h = rnd.randint(0, n)
            exact = rnd.random() < 0.5
            want = [ln for ln in expected if (sum(a == b for a, b in zip(ln, result)) == h if exact else sum(a == b for a, b in zip(ln, result)) >= h)]
            cl = call(base, "/api/matchx/check-lines", {**body, "result": result, "minHits": h, "exact": exact})
            check(cl.get("total") == len(want) and sorted(map(tuple, all_lines(base, cl["id"], cl["total"]))) == sorted(map(tuple, want)),
                  f"round {it}: check-lines h={h} exact={exact}")

    for body, fragment in [
        ({"matches": 3, "tickets": [{"groups": [{"type": "full", "matches": [1, 2], "picks": [["1", "U"], ["X"]]}]}]}, "different kinds"),
        ({"matches": 3, "tickets": [{"groups": [{"type": "full", "matches": [1, 2], "picks": [["1"], ["X"]]},
                                                {"type": "full", "matches": [2, 3], "picks": [["1"], ["X"]]}]}]}, "two groups"),
        ({"matches": 3, "tickets": [{"groups": [{"type": "errors", "matches": [1], "picks": [["1", "X"]], "errors": [5]}]}]}, "errors"),
        ({"matches": 2, "tickets": [{"groups": [{"type": "nope", "matches": [1], "picks": [["1"]]}]}]}, "Unknown group type"),
    ]:
        r = call(base, "/api/matchx/count", body)
        check("__err" in r and fragment in r["__err"], f"error message for {body}: {r}")

    print(f"{ok} OK, {bad} FAIL")
    sys.exit(1 if bad else 0)


if __name__ == "__main__":
    main()
