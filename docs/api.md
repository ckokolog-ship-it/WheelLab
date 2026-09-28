# API reference

The WheelLab server speaks JSON over HTTP. It keeps no user data: every request carries the whole ticket or
slip. Built systems are kept only as lazy line sources (the most recent 32), read page by page with
`/api/lines`. Errors return HTTP 400 with `{"error": "..."}`; an unknown or expired build id returns 404.

## MatchX

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

## Number games (LOTTO, KENO, My Games)

`game` is `{numbers, pick, draw}` (LOTTO `{49, 6, 6}`, KENO `{80, spots, 20}`) or
`{title, min, max, pick, draw, bonus?: {label, min, max, pick, draw}}`; entries take `"bonus": [..]` in games
with a bonus pool:

| Endpoint | |
|---|---|
| `POST /api/pick/count` | `{game, entries}` → `{total, entries:[{type, count}]}` |
| `POST /api/pick/build` | → `{id, total, perEntry}` |
| `POST /api/pick/check` | `+ drawn, drawnBonus?, payouts?` → `{total, histogram, grid?, entries:[{histogram, grid?}], payout?}` -- `payouts[h]` or `payouts[h][s]` |
| `POST /api/pick/check-lines` | `+ drawn, drawnBonus?, minHits, exact, minBonus?` → `{id, total}` |
| `POST /api/pick/position` | `{game, lines:["1 2 3 4 5 6"], pool?, bonusPool?}` → `{total, rows:[{line, rank}]}` (bonus after `|`) |
| `POST /api/pick/analysis` | `{game, entries, entry}` (a System entry) → `{total, oddEven, lastDigit}` |
| `POST /api/pick/frequency` | `{game, entries}` → `{total, main:[{number, count}], bonus}` |
| `POST /api/pick/parse` | `{text}` → `{game, entries, prizes}` (game language; errors name the line) |
| `POST /api/pick/text` | `{game, entries, prizes?}` → `{text}` |

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
