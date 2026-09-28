package io.wheellab.matchx;

import io.wheellab.core.Checks;
import io.wheellab.core.LineSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Ranges system: the picks of every match are in order of preference (1st, 2nd, 3rd choice). A line takes
 * one pick per match, and is played only if the number of matches on their 1st choice is within
 * {@code ranges[0]}, on their 2nd choice within {@code ranges[1]}, and on their 3rd within {@code ranges[2]}.
 * <p>
 * Lines are in lexicographic order of the choices (match by match, 1st choice first).
 */
public final class RangesGroup extends Group {

    public static final int RANKS = 3;

    private final int[][] ranges;
    /** memo[i][a][b] = lines for matches i.. given a 1st and b 2nd choices so far (-1 = not computed). */
    private final long[][][] memo;

    public RangesGroup(List<Integer> matches, List<List<String>> picks, int[][] ranges) {
        super(matches, picks);
        int g = picks.size();
        Checks.that(ranges != null && ranges.length == RANKS, "Ranges group: give a from-to range for the 1st, 2nd and 3rd choice.");
        for (int r = 0; r < RANKS; r++)
            Checks.that(ranges[r].length == 2 && ranges[r][0] >= 0 && ranges[r][0] <= ranges[r][1],
                    "Ranges group: each range needs from <= to (and not negative).");
        this.ranges = new int[RANKS][];
        for (int r = 0; r < RANKS; r++) this.ranges[r] = ranges[r].clone();
        memo = new long[g + 1][g + 1][g + 1];
        for (long[][] a : memo) for (long[] b : a) Arrays.fill(b, -1);
        completions(0, 0, 0); // fills the memo up front, so reading lines from several threads only reads it
    }

    @Override
    public String type() {
        return "ranges";
    }

    public int[][] ranges() {
        return ranges;
    }

    private long completions(int i, int a, int b) {
        int c = i - a - b;
        if (a > ranges[0][1] || b > ranges[1][1] || c > ranges[2][1]) return 0;
        int g = picks.size();
        if (i == g) return a >= ranges[0][0] && b >= ranges[1][0] && c >= ranges[2][0] ? 1 : 0;
        if (memo[i][a][b] >= 0) return memo[i][a][b];
        long total = 0;
        for (int r = 0; r < picks.get(i).size(); r++) total = Math.addExact(total, completions(i + 1, a + (r == 0 ? 1 : 0), b + (r == 1 ? 1 : 0)));
        memo[i][a][b] = total;
        return total;
    }

    @Override
    public long count() {
        return completions(0, 0, 0);
    }

    @Override
    public LineSource<String> lines() {
        long total = count();
        return new LineSource<>() {
            @Override
            public long size() {
                return total;
            }

            @Override
            public List<String> get(long index) {
                Checks.index(index, total);
                List<String> line = new ArrayList<>(picks.size());
                long rest = index;
                int a = 0, b = 0;
                for (int i = 0; i < picks.size(); i++) {
                    List<String> p = picks.get(i);
                    for (int r = 0; r < p.size(); r++) {
                        int na = a + (r == 0 ? 1 : 0), nb = b + (r == 1 ? 1 : 0);
                        long block = completions(i + 1, na, nb);
                        if (rest < block) {
                            line.add(p.get(r));
                            a = na;
                            b = nb;
                            break;
                        }
                        rest -= block;
                    }
                }
                return line;
            }
        };
    }

    /** Dynamic programming over (1st choices, 2nd choices, hits). */
    @Override
    public long[] histogram(List<String> result) {
        int g = picks.size();
        long[][][] dp = new long[g + 1][g + 1][g + 1];
        dp[0][0][0] = 1;
        for (int i = 0; i < g; i++) {
            List<String> p = picks.get(i);
            long[][][] next = new long[g + 1][g + 1][g + 1];
            for (int a = 0; a <= i; a++)
                for (int b = 0; a + b <= i; b++)
                    for (int h = 0; h <= i; h++) {
                        long v = dp[a][b][h];
                        if (v == 0) continue;
                        for (int r = 0; r < p.size(); r++) {
                            int na = a + (r == 0 ? 1 : 0), nb = b + (r == 1 ? 1 : 0);
                            int nh = h + (p.get(r).equals(result.get(i)) ? 1 : 0);
                            next[na][nb][nh] = Math.addExact(next[na][nb][nh], v);
                        }
                    }
            dp = next;
        }
        long[] hist = new long[g + 1];
        for (int a = 0; a <= g; a++)
            for (int b = 0; a + b <= g; b++) {
                int c = g - a - b;
                if (a < ranges[0][0] || a > ranges[0][1] || b < ranges[1][0] || b > ranges[1][1] || c < ranges[2][0] || c > ranges[2][1]) continue;
                for (int h = 0; h <= g; h++) hist[h] = Math.addExact(hist[h], dp[a][b][h]);
            }
        return hist;
    }
}
