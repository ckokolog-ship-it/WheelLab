# LOTTO and KENO guide

Both are number games: a line is a set of numbers, some numbers are drawn, and a line's **hits** are how
many of its numbers were drawn.

| Game | Numbers | A line has | Drawn |
|---|---|---|---|
| **LOTTO** | 1-49 (you can set 5-100) | 6 (you can set 1-20) | as many as a line has |
| **KENO** | 1-80 | 1 to 12 "spots" | 20 |

## Entries

A slip is a list of **entries**; its lines are the lines of every entry, one after another.

| Entry | Lines |
|---|---|
| **Single** | one line |
| **System** | every combination of *k* numbers from the numbers you pick -- 8 numbers in LOTTO = C(8,6) = **28 lines** |
| **Groups** | take a set count from each group and combine them -- 2 of 4 × 4 of 5 = 6 × 5 = **30 lines**; a number belongs to one group only |
| **Wheel** | a reduced system on your numbers with a guarantee |

### Wheels

> If at least **G** of the drawn numbers are among your numbers, some line has at least **G** hits.

WheelLab makes sure every group of G of your numbers sits together in some line, then checks it
exhaustively. For example 20 numbers, lines of 6, G = 3 gives about a hundred lines instead of the 38,760 of
the full system. The wheels are valid but not always the smallest known; the same numbers always give the
same wheel.

## Check

Pick the drawn numbers. You get how many lines have each number of hits -- for the whole slip and for every
entry -- computed with exact formulas, so it is instant even for systems with millions of lines.

**Prizes**: you can type a prize per number of hits (per line). WheelLab multiplies it by the number of lines
with those hits and adds everything up. There is no built-in prize table: prizes differ by operator and
country -- use the ones that apply to you.

Then **Show lines** lists the lines with at least (or exactly) *h* hits.

## Odds

The chance that one line gets exactly *h* hits:

```
P(h) = C(k, h) · C(N − k, d − h) / C(N, d)
```

with *N* numbers, *k* numbers per line and *d* drawn. LOTTO 6 of 49: 6 hits = 1 in 13,983,816. KENO 5 spots:
5 hits = 1 in 1,551. With prizes set in Check, the expected prize per line is shown too.

## Position

Where a line is among all lines of the game (or of a system on your numbers), in lexicographic order: in
LOTTO 6 of 49, `1 2 3 4 5 6` is line 1 and `44 45 46 47 48 49` is line 13,983,816.
