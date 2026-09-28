package io.wheellab.matchx;

import io.wheellab.core.Checks;
import io.wheellab.core.LineSource;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Errors system: the first pick of every match is the prediction; a line may differ from the prediction
 * ("an error") on some of the matches that have more than one pick. The group plays the lines with exactly
 * {@code e} errors, for every {@code e} in {@link #errors()}.
 * <p>
 * Lines come error count by error count (ascending); within a count, match by match the prediction first
 * and then the other picks in their order.
 */
public final class ErrorsGroup extends Group {

    private final List<Integer> errors;
    /** ways[i][e] = lines of matches i.. with exactly e errors. */
    private final long[][] ways;

    public ErrorsGroup(List<Integer> matches, List<List<String>> picks, List<Integer> errors) {
        super(matches, picks);
        int g = picks.size();
        Checks.that(errors != null && !errors.isEmpty(), "Errors group: choose how many errors to play (e.g. 0,1,2).");
        TreeSet<Integer> set = new TreeSet<>(errors);
        Checks.that(set.first() >= 0 && set.last() <= g, "Errors group: the number of errors is 0 to " + g + " (the matches of the group).");
        this.errors = List.copyOf(set);
        ways = new long[g + 1][g + 2];
        ways[g][0] = 1;
        for (int i = g - 1; i >= 0; i--) {
            int alt = picks.get(i).size() - 1;
            for (int e = 0; e <= g; e++) {
                long w = ways[i + 1][e];
                if (e > 0) w = Math.addExact(w, Math.multiplyExact(alt, ways[i + 1][e - 1]));
                ways[i][e] = w;
            }
        }
    }

    @Override
    public String type() {
        return "errors";
    }

    public List<Integer> errors() {
        return errors;
    }

    @Override
    public LineSource<String> lines() {
        List<LineSource<String>> parts = new ArrayList<>();
        for (int e : errors) {
            long size = ways[0][e];
            if (size == 0) continue;
            parts.add(new LineSource<>() {
                @Override
                public long size() {
                    return size;
                }

                @Override
                public List<String> get(long index) {
                    Checks.index(index, size);
                    List<String> line = new ArrayList<>(picks.size());
                    long rest = index;
                    int left = e;
                    for (int i = 0; i < picks.size(); i++) {
                        List<String> p = picks.get(i);
                        long keep = ways[i + 1][left];
                        if (rest < keep) {
                            line.add(p.get(0));
                            continue;
                        }
                        rest -= keep;
                        long each = ways[i + 1][left - 1];
                        int a = (int) (rest / each);
                        line.add(p.get(1 + a));
                        rest -= a * each;
                        left--;
                    }
                    return line;
                }
            });
        }
        return LineSource.concat(parts);
    }

    @Override
    public long count() {
        long total = 0;
        for (int e : errors) total = Math.addExact(total, ways[0][e]);
        return total;
    }

    /** Dynamic programming over (errors so far, hits so far). */
    @Override
    public long[] histogram(List<String> result) {
        int g = picks.size();
        long[][] dp = new long[g + 1][g + 1];
        dp[0][0] = 1;
        for (int i = 0; i < g; i++) {
            List<String> p = picks.get(i);
            boolean baseHit = p.get(0).equals(result.get(i));
            boolean altHit = !baseHit && p.contains(result.get(i));
            int alt = p.size() - 1;
            long[][] next = new long[g + 1][g + 1];
            for (int e = 0; e <= i; e++)
                for (int h = 0; h <= i; h++) {
                    long v = dp[e][h];
                    if (v == 0) continue;
                    int hb = h + (baseHit ? 1 : 0);
                    next[e][hb] = Math.addExact(next[e][hb], v);
                    if (alt > 0) {
                        if (altHit) {
                            next[e + 1][h + 1] = Math.addExact(next[e + 1][h + 1], v);
                            if (alt > 1) next[e + 1][h] = Math.addExact(next[e + 1][h], Math.multiplyExact(v, alt - 1));
                        } else {
                            next[e + 1][h] = Math.addExact(next[e + 1][h], Math.multiplyExact(v, alt));
                        }
                    }
                }
            dp = next;
        }
        long[] hist = new long[g + 1];
        for (int e : errors)
            for (int h = 0; h <= g; h++) hist[h] = Math.addExact(hist[h], dp[e][h]);
        return hist;
    }
}
