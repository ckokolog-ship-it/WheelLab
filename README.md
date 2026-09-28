# WheelLab

[![CI](https://github.com/ckokolog-ship-it/WheelLab/actions/workflows/ci.yml/badge.svg)](https://github.com/ckokolog-ship-it/WheelLab/actions/workflows/ci.yml)

Build and check **full, reduced and custom systems** for match-result predictions and number games -- with
exact counts and hit statistics for systems of any size, without generating a single line until you ask to
see them.

| Game | What you do |
|---|---|
| **MatchX** | predict up to 20 matches (1 X 2, Under/Over, GG/NG) with Full, Errors, Ranges and Covering systems |
| **LOTTO** | 6 from 1-49 (adjustable): single lines, systems, groups and wheels |
| **KENO** | 1-12 spots from 1-80, 20 drawn |
| **My Games** | number games you define -- any range, pick and draw, an optional bonus pool, and a text language |

In every game: **Check** a result instantly (hit tables for systems of any size, with your own prizes),
list the lines with a given number of hits, see the exact **odds**, **analyse** systems (odd/even, last
digit, frequencies) and find the **position** of any line.

## Quick start

Requirements: Java 17+, Maven 3.9+, Node.js 20+.

```bash
cd web && npm install && npm run build
cd ../server && mvn package
java -jar target/wheellab-server.jar --web ../web/dist      # open http://localhost:8090
```

For development, run the server (`java -jar target/wheellab-server.jar`, API on port 8090) and the web app
with hot reload (`cd web && npm run dev`, http://localhost:5173 -- `/api` is proxied to the server).

Server options: `--port N` (or `WHEELLAB_PORT`), `--web DIR`. The server keeps no user data; the web app
remembers your tickets and games in the browser and saves them as JSON (or text) files.

## Documentation

| Guide | |
|---|---|
| [MatchX](docs/matchx.md) | tickets, groups, the four systems, check |
| [LOTTO and KENO](docs/lotto-keno.md) | entries, wheels, prizes, odds, position |
| [My Games](docs/my-games.md) | your own games, bonus pools, the game language, analyses |
| [How it works](docs/how-it-works.md) | lazy lines, formulas instead of scanning, proved reduced systems |
| [API reference](docs/api.md) | every endpoint, with request examples |

## Verified

Every count, line and hit table is checked against an independent brute force -- JUnit tests in
`server/src/test`, and end-to-end scripts in `tools/verify` that compare a running server with enumeration
written separately in Python. CI runs all of them on every pull request.

```bash
cd server && mvn verify
python3 tools/verify/verify_matchx.py --url http://localhost:8090
python3 tools/verify/verify_pick.py --url http://localhost:8090
```

## Project layout

```
server/        Java engine and API (io.wheellab.core, .matchx, .pick, .api)
web/           React app (src/matchx, src/pick, src/components)
tools/verify/  end-to-end verification scripts
docs/          guides
```

## Contributing

WheelLab is published so it can be read and tried; it is not open source (see the license). Bug reports and
suggestions are welcome as GitHub issues. Pull requests cannot be accepted at the moment.

## Disclaimer

WheelLab computes combinations, systems and probabilities. It does **not** predict results and does not
promise any winnings. It is not affiliated with any lottery or betting operator. Play responsibly and only
where it is legal for you.

## License

Copyright © 2026 ckokolog-ship-it. All rights reserved -- see [LICENSE](LICENSE). Third-party libraries:
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
