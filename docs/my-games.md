# My Games guide

**My Games** are number games you define yourself. A game has:

| Setting | Example |
|---|---|
| Numbers from-to | 1-50 (any range of up to 100 numbers, from 0 up) |
| Numbers per line (pick) | 5 (1-20) |
| Numbers drawn | 5 (for KENO-like games, more than a line has) |
| Optional second pool | "Star" 1-12, pick 2, 2 drawn |

A game is then played exactly like LOTTO and KENO -- Single lines, Systems, Groups and Wheels, Check,
Odds, Analysis and Position (see [lotto-keno.md](lotto-keno.md)) -- with a few additions for the second pool.

Games stay in your browser. Use 💾 to save one as a JSON file, ⬇ to export it as text, and **Open file** to
load either.

## The second pool (bonus)

Every line carries bonus numbers too; they are shown as `+n` in the lines.

| Entry | Bonus numbers |
|---|---|
| Single, Groups, Wheel | exactly the bonus pick |
| System | the bonus pick **or more** -- every combination of them |

*Example.* A system of 7 numbers with Stars 2 7 11 (pick 2): C(7,5) × C(3,2) = 21 × 3 = **63 lines**.

**Check** then shows, for every number of main hits, how many lines had 0, 1, … bonus hits. **Prizes** can be
set per combination -- "5 + 2" -- or for a number of main hits with any bonus hits -- "5".

## Game language

A whole game -- settings, prizes and entries -- as plain text. Create a game from text with **From text**,
and export any game with ⬇.

```
# Lines starting with # are comments.
game "Star 5" numbers 1-50 pick 5 draw 5
bonus "Star" numbers 1-12 pick 2 draw 2
prize 5+2 = 1000000
prize 5+1 = 50000
prize 5 = 10000
entry single 3 11 17 25 38 | 4 9
entry system 3 8 15 22 29 36 43 | 2 7 11
entry groups 1 2 3 4 take 2 ; 40 41 42 43 44 take 3 | 5 9
entry wheel 1 5 9 13 17 21 25 29 33 37 guarantee 3 | 1 2
```

| Line | Meaning |
|---|---|
| `game "Name" numbers A-B pick K [draw D]` | the game (first line); `draw` defaults to `pick` |
| `bonus "Name" numbers A-B pick K [draw D]` | the optional second pool |
| `prize H+S = amount` | prize per line with H main hits and S bonus hits |
| `prize H = amount` | prize per line with H main hits (any bonus hits) |
| `entry single N N N … [| bonus]` | one line |
| `entry system N N N … [| bonus]` | a full system (bonus: the pick or more) |
| `entry groups N N … take T ; N N … take T [| bonus]` | groups, separated by `;` |
| `entry wheel N N N … guarantee G [| bonus]` | a wheel |

If something is wrong, the message names the line and shows it, e.g.
`Line 6: Single line: exactly 5 numbers.` Exporting a game and reading it back gives the same text.

## Analysis

Available in every number game (LOTTO, KENO and My Games):

- **Odd / even and last digit** -- for a System entry: how many of its lines have 0, 1, … odd numbers
  (and whether odd or even numbers are the majority), and how many have 0, 1, … numbers ending in each
  digit 0-9. The counts are exact formulas, C(m, j) × C(v − m, k − j) times the bonus combinations, so they
  work for systems of any size.
- **Frequency** -- in how many lines of the whole slip every number (and every bonus number) appears.
