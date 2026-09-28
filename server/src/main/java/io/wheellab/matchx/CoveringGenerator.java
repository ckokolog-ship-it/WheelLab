package io.wheellab.matchx;

import io.wheellab.core.Checks;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Builds a reduced ("covering") system with a guarantee:
 * <blockquote>if the results differ from the prediction on at most {@code radius} matches, at least one line
 * has at least {@code guarantee} correct outcomes.</blockquote>
 * The universe is every result within {@code radius} changes of the prediction (using only the outcomes
 * played on each match). A randomized greedy search picks lines until every result of the universe is
 * covered; the result is then re-checked exhaustively. The seed is fixed, so the same input always gives
 * the same lines. The system is valid but not necessarily the smallest possible.
 */
public final class CoveringGenerator {

    /** Above this many results to cover the search gets slow -- ask for a smaller radius or group. */
    public static final int MAX_UNIVERSE = 20_000;
    private static final int CANDIDATES_PER_STEP = 48;
    private static final long SEED = 20260928L;
    private static final int CACHE_SIZE = 64;

    private static final Map<String, List<List<String>>> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<List<String>>> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private CoveringGenerator() {
    }

    /** {@code picks.get(i)}: outcomes played on match i, the first one being the prediction. */
    public static List<List<String>> generate(List<List<String>> picks, int radius, int guarantee) {
        int n = picks.size();
        Checks.that(radius >= 0 && radius <= n, "Covering group: the changes to cover are 0 to " + n + ".");
        Checks.that(guarantee >= 1 && guarantee <= n, "Covering group: the guaranteed hits are 1 to " + n + ".");
        String key = picks + "|" + radius + "|" + guarantee;
        synchronized (CACHE) {
            List<List<String>> cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        List<List<String>> lines = search(picks, radius, guarantee);
        synchronized (CACHE) {
            CACHE.put(key, lines);
        }
        return lines;
    }

    private static List<List<String>> search(List<List<String>> picks, int radius, int guarantee) {
        int n = picks.size();
        int[] sizes = picks.stream().mapToInt(List::size).toArray();
        long universeSize = ballSize(sizes, radius);
        Checks.that(universeSize <= MAX_UNIVERSE, "Covering group: " + universeSize + " results to cover -- at most "
                + MAX_UNIVERSE + ". Use fewer changes, fewer matches or fewer outcomes per match.");
        List<int[]> uncovered = ball(sizes, radius);
        List<int[]> universe = new ArrayList<>(uncovered);
        Random rnd = new Random(SEED);
        List<int[]> chosen = new ArrayList<>();
        while (!uncovered.isEmpty()) {
            int[] best = null;
            int bestCount = -1;
            for (int c = 0; c < Math.min(CANDIDATES_PER_STEP, uncovered.size()); c++) {
                // Start from an uncovered result and change at most n - guarantee matches: the candidate
                // still covers that result, and may cover others too.
                int[] candidate = uncovered.get(rnd.nextInt(uncovered.size())).clone();
                int changes = n - guarantee > 0 ? rnd.nextInt(n - guarantee + 1) : 0;
                for (int ch = 0; ch < changes; ch++) {
                    int pos = rnd.nextInt(n);
                    candidate[pos] = rnd.nextInt(sizes[pos]);
                }
                int count = 0;
                for (int[] p : uncovered) if (agreement(candidate, p) >= guarantee) count++;
                if (count > bestCount) {
                    bestCount = count;
                    best = candidate;
                }
            }
            int[] pick = best;
            chosen.add(pick);
            uncovered.removeIf(p -> agreement(pick, p) >= guarantee);
        }
        for (int[] p : universe) {
            boolean ok = false;
            for (int[] line : chosen) if (agreement(line, p) >= guarantee) { ok = true; break; }
            if (!ok) throw new IllegalStateException("Covering group: internal check failed for " + Arrays.toString(p));
        }
        List<List<String>> out = new ArrayList<>();
        for (int[] line : chosen) {
            List<String> symbols = new ArrayList<>(n);
            for (int i = 0; i < n; i++) symbols.add(picks.get(i).get(line[i]));
            out.add(List.copyOf(symbols));
        }
        return List.copyOf(out);
    }

    static int agreement(int[] a, int[] b) {
        int same = 0;
        for (int i = 0; i < a.length; i++) if (a[i] == b[i]) same++;
        return same;
    }

    /** Number of results with at most {@code radius} matches away from the prediction (index 0). */
    static long ballSize(int[] sizes, int radius) {
        long[] e = new long[radius + 1]; // e[j] = results with exactly j changes so far
        e[0] = 1;
        for (int s : sizes)
            for (int j = radius; j >= 1; j--) e[j] = Math.addExact(e[j], Math.multiplyExact(e[j - 1], s - 1));
        long total = 0;
        for (long v : e) total = Math.addExact(total, v);
        return total;
    }

    private static List<int[]> ball(int[] sizes, int radius) {
        List<int[]> out = new ArrayList<>();
        grow(sizes, radius, 0, new int[sizes.length], out);
        return out;
    }

    private static void grow(int[] sizes, int left, int i, int[] cur, List<int[]> out) {
        if (i == sizes.length) {
            out.add(cur.clone());
            return;
        }
        cur[i] = 0;
        grow(sizes, left, i + 1, cur, out);
        if (left == 0) return;
        for (int v = 1; v < sizes[i]; v++) {
            cur[i] = v;
            grow(sizes, left - 1, i + 1, cur, out);
        }
        cur[i] = 0;
    }
}
