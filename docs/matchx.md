# MatchX guide

MatchX is for predicting the outcome of a set of matches -- up to 20 -- where every match is played on one
market: **1 X 2**, **Under / Over** or **GG / NG**.

## Tickets and groups

A **ticket** covers the matches `1 … N`. It is split into **groups**: every group takes some of the matches
(not necessarily consecutive, never shared with another group) and plays them with one **system**. The
lines of the ticket are every combination of one line from each group:

```
lines(ticket) = lines(group A) × lines(group B) × …
```

A match that no group takes shows `-` in every line and never counts as a hit.

Several **tickets** can be combined: their lines are simply put one after another (ticket 1, then ticket 2,
…). Each ticket is independent -- the same match may be predicted differently on different tickets.

## Systems

### Full

Every combination of the outcomes you pick.

*Example.* Match 1 `1 X`, match 2 `U O`, match 3 `2` → 2 × 2 × 1 = **4 lines**.

### Errors

The **first** outcome you pick on each match is your **prediction**; the others are the alternatives. A
line has an *error* on a match when it takes an alternative there. You choose which numbers of errors to
play, e.g. `0,1`.

*Example.* Three matches, all `1 X 2` with prediction `1`, errors `0,1`:
0 errors → 1 line; 1 error → 3 matches × 2 alternatives = 6 lines; **7 lines**.

With lines of *e* errors, if the result differs from your prediction on exactly *e* matches that you
covered with alternatives, one of the lines is all correct.

### Ranges

The outcomes of each match are in **order of preference** -- 1st, 2nd (and 3rd) choice. You limit how many
matches take their 1st choice, how many their 2nd and how many their 3rd:

*Example.* Four matches `1 › X › 2`; 1st choice 2 to 4, 2nd choice 0 to 2, 3rd choice 0 to 0:
the lines take `1` on at least two matches and `X` elsewhere, never `2` → C(4,2) + C(4,3) + C(4,4) = **11 lines**.

A match with only two outcomes has no 3rd choice; a match with one outcome always takes its 1st choice.

### Covering

A **reduced** system with a guarantee you choose:

> If at most **R** matches differ from my prediction (the first pick of each match), some line has at
> least **G** hits.

WheelLab searches for a small set of lines with that guarantee and then checks it against every possible
result within R changes. The set is valid but not always the smallest possible. The same input always
gives the same lines.

*Example.* Four matches `1 X 2` (prediction `1`), R = 1, G = 3: any result with at most one mistake is
matched on at least 3 of the 4 matches by some line.

## Check

Enter the result of every match (the market of each match comes from your groups). For every ticket, and
for every group, you get how many lines have 0, 1, …, N hits. This is computed from the systems themselves,
so it is instant even for systems with billions of lines.

Then **Show lines** lists the lines with at least (or exactly) *h* hits -- up to 200,000 of them, from
systems of up to 20 million lines.

## Saving

The web app remembers your tickets in the browser. **Save** downloads them as a JSON file; **Open** loads
such a file.
