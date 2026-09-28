# WheelLab

Build and check **full, reduced and custom systems** for match-result predictions and number games --
with exact counts and hit statistics for systems of any size, without generating a single line until you
ask to see them.

> **Status:** early. Games: **MatchX**, **LOTTO** and **KENO**; "My Games" (any pick-K-of-N game,
> defined by you) is next.

## MatchX

Predict the outcome of up to 20 matches. Each match is played on one market:

| Market | Outcomes |
|---|---|
| 1 X 2 | home win, draw, away win |
| Under / Over | `U`, `O` |
| GG / NG | both teams score, not both |

A **ticket** splits the matches into **groups**; each group has its own system, and the ticket's lines are
every combination of its groups' lines. Several tickets can be combined.

| System | What it plays |
|---|---|
| **Full** | every combination of the outcomes you pick |
| **Errors** | your prediction (first pick of each match), with exactly *e* wrong matches for each *e* you choose |
| **Ranges** | picks in order of preference; limit how many matches take their 1st, 2nd and 3rd choice |
| **Covering** | a reduced system: *if at most R matches differ from your prediction, some line has at least G hits* |

**Check** a result: the number of lines with each number of hits comes out instantly, for every ticket and
every group -- computed from the systems, not by scanning lines. Then list the lines with (at least) *h*
hits.

See [docs/matchx.md](docs/matchx.md) for the details and examples.

## LOTTO and KENO

Number games: **LOTTO** (6 from 1-49 by default, both adjustable) and **KENO** (1-12 spots from 1-80, 20
drawn). Build a slip of entries -- **Single** lines, full **Systems**, **Groups** (A × B × …) and
**Wheels** (reduced systems: *if at least G of the drawn numbers are among yours, some line has at least G
hits*). Check a draw with your own prize per number of hits, see the exact odds, and find where any line is
among all lines of the game.

See [docs/lotto-keno.md](docs/lotto-keno.md).

## Run it

Requirements: Java 17+, Maven 3.9+, Node.js 20+.

```bash
# server (API on http://localhost:8090)
cd server
mvn package
java -jar target/wheellab-server.jar

# web app (development, http://localhost:5173 -- /api is proxied to the server)
cd web
npm install
npm run dev
```

To serve the built web app from the server itself:

```bash
cd web && npm run build
cd ../server && java -jar target/wheellab-server.jar --web ../web/dist   # http://localhost:8090
```

Options: `--port N` (or `WHEELLAB_PORT`), `--web DIR`. The server keeps no user data -- every request carries
the whole ticket; the web app remembers your ticket in the browser and can save/open it as a JSON file.

## How it is verified

- `server/src/test` -- every system type against a plain enumeration of all combinations (lines, counts,
  hit histograms), the covering guarantee checked exhaustively, and tickets combining groups.
- `tools/verify/verify_matchx.py` -- end-to-end against a running server: random tickets (all systems,
  all markets, several tickets), every built line, every check, compared with an independent brute force.
- `tools/verify/verify_pick.py` -- the same for LOTTO, KENO and other N / k / draw settings: every entry
  type, checks with prizes, show lines, wheel guarantees and line positions.

```bash
cd server && mvn test
python3 tools/verify/verify_matchx.py --url http://localhost:8090
python3 tools/verify/verify_pick.py --url http://localhost:8090
```

## API

| Endpoint | |
|---|---|
| `GET /api/health` | `{status, app}` |
| `POST /api/matchx/count` | `{matches, tickets}` → `{total, tickets:[{total, uncovered, groups:[{type, matches, count}]}]}` |
| `POST /api/matchx/build` | → `{id, total, perTicket}` -- read the lines with `/api/lines` |
| `GET /api/lines?id=&start=&size=` | a page of lines (up to 1000): `{total, start, lines}` |
| `POST /api/matchx/check` | `+ result` → `{total, histogram, tickets:[{histogram, groups:[..]}]}` |
| `POST /api/matchx/check-lines` | `+ result, minHits, exact` → `{id, total}` |

A request body:

```json
{
  "matches": 6,
  "tickets": [{
    "groups": [
      {"type": "full",     "matches": [1, 2], "picks": [["1", "X"], ["U", "O"]]},
      {"type": "errors",   "matches": [3, 4], "picks": [["1", "X", "2"], ["GG", "NG"]], "errors": [0, 1]},
      {"type": "covering", "matches": [5, 6], "picks": [["1", "X"], ["2", "X"]], "radius": 1, "guarantee": 2}
    ]
  }]
}
```

Number games (`game` = `{numbers, pick, draw}` -- LOTTO `{49, 6, 6}`, KENO `{80, spots, 20}`):

| Endpoint | |
|---|---|
| `POST /api/pick/count` | `{game, entries}` → `{total, entries:[{type, count}]}` |
| `POST /api/pick/build` | → `{id, total, perEntry}` |
| `POST /api/pick/check` | `+ drawn, payouts?` → `{total, histogram, entries:[{histogram}], payout?}` |
| `POST /api/pick/check-lines` | `+ drawn, minHits, exact` → `{id, total}` |
| `POST /api/pick/position` | `{game, lines:["1 2 3 4 5 6"], pool?}` → `{total, rows:[{line, rank}]}` |

```json
{
  "game": {"numbers": 49, "pick": 6, "draw": 6},
  "entries": [
    {"type": "single", "numbers": [3, 11, 17, 25, 38, 44]},
    {"type": "system", "numbers": [1, 2, 3, 4, 5, 6, 7, 8]},
    {"type": "groups", "groups": [[1, 2, 3, 4], [20, 21, 22, 23, 24]], "take": [2, 4]},
    {"type": "wheel",  "numbers": [1, 5, 9, 13, 17, 21, 25, 29, 33, 37], "guarantee": 3}
  ]
}
```

Errors return HTTP 400 with `{"error": "a message meant for the user"}`.

## Disclaimer

WheelLab computes combinations, systems and probabilities. It does **not** predict results and does not
promise any winnings. It is not affiliated with any lottery or betting operator. Play responsibly and only
where it is legal for you.

## License

Copyright © 2026 ckokolog-ship-it. All rights reserved -- see [LICENSE](LICENSE). Third-party libraries:
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
