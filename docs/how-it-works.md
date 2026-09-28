# How it works

WheelLab answers questions about systems with **billions of lines** -- how many lines, which line is at
position *i*, how many lines hit *h* -- without ever building them. This page explains the three ideas that
make that possible, and how the results are verified.

## 1. Lines are computed, not stored

Every system is a **lazy line source**: it knows its size and can compute the line at any position on
demand (`LineSource.get(index)`). Paging through a system of 4 billion lines reads only the 50 lines on
screen.

- **Combinations** use the *combinatorial number system*: the *i*-th *k*-subset of a pool, in lexicographic
  order, is found digit by digit with binomial coefficients (`Combinatorics.combinationAt`), and the inverse
  gives a line's position (`Combinatorics.rank`) -- that is how **Position** works.
- **Products** (a MatchX ticket = group × group × …; LOTTO groups = A × B × …; a line × bonus combinations)
  use mixed-radix digits: the last part changes fastest.
- **Constrained systems** (MatchX Errors and Ranges) count completions with dynamic programming --
  `ways(match i, errors left)`, `completions(match i, 1st choices, 2nd choices)` -- and walk down those
  counts to unrank a line in O(matches).

## 2. Hit statistics come from formulas

A **Check** never scans lines. For each part of a system it computes the hit histogram directly, then
combines independent parts by **convolution** (hits add up):

| Part | Histogram of hits |
|---|---|
| MatchX Full | per match: `[n−1, 1]` if the result is among the *n* picks, else `[n]`; convolve over matches |
| MatchX Errors | DP over (errors so far, hits so far) |
| MatchX Ranges | DP over (1st choices, 2nd choices, hits), keeping only the allowed ranges |
| System of *v* numbers, *a* of them drawn | `C(a, h) · C(v − a, k − h)` |
| Groups | the system formula per group, convolved |
| Bonus pool | the same formula for the bonus pick, combined as a 2-D grid (main × bonus) |
| Wheels, covering systems, single lines | a few lines -- counted one by one |

The same approach gives the **Analysis** tables (lines with *j* odd numbers: `C(odd, j) · C(even, k − j)`)
and **Frequency** (a number of a *v*-number system is in `C(v − 1, k − 1)` lines).

## 3. Reduced systems are searched, then proved

**Covering** (MatchX) and **Wheel** (number games) systems come with a guarantee. WheelLab builds them with a
randomized greedy search -- each step starts from a still-uncovered case and keeps the candidate line that
covers the most -- and then **checks the guarantee exhaustively** before returning anything. The seed is
fixed, so the same input always gives the same lines (a count and a later build agree). The result is valid,
though not always the smallest known.

## Verification

Every formula has an independent check that simply enumerates:

- **JUnit** (`server/src/test`): every system type against a plain enumeration of all combinations -- the
  same lines, no duplicates, the same counts, the same hit histograms, the same frequencies; covering and
  wheel guarantees checked case by case; rank as the exact inverse of unranking.
- **End-to-end** (`tools/verify`): Python scripts generate random tickets and slips (all systems, markets,
  games with and without a bonus pool), call the running server, read every built line, and compare
  everything -- lines, counts, checks, prizes, positions, analyses, the game language -- with a brute force
  written separately in Python.

Both run on every pull request (see `.github/workflows/ci.yml`).

## Architecture

```
web/  (React + Vite)                     server/  (Java 17, JDK HTTP server + Gson)
  MatchX, LOTTO, KENO, My Games   ──►      api/      JSON endpoints, paged line store
  state in the browser, JSON files        matchx/   tickets, groups, covering generator
                                          pick/     number games, entries, wheels, game language
                                          core/     lazy line sources, combinatorics, histograms
```

The server is stateless apart from a small cache of recent builds, so it can be restarted at any time; the
web app keeps your tickets and games in the browser and in files you save.
