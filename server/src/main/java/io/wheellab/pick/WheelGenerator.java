package io.wheellab.pick;

import io.wheellab.core.Checks;
import io.wheellab.core.Combinatorics;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Builds a wheel (a covering design) on a pool of numbers with a guarantee:
 * <blockquote>if at least {@code guarantee} of the drawn numbers are in the pool, some line has at least
 * {@code guarantee} hits.</blockquote>
 * That holds when every {@code guarantee}-subset of the pool lies inside some line. A randomized greedy
 * search adds lines until all such subsets are covered, then the result is re-checked exhaustively. The seed
 * is fixed, so the same pool always gives the same lines. Valid, not necessarily the smallest possible.
 */
public final class WheelGenerator {

    public static final int MAX_POOL = 60;
    /** Subsets to cover: C(pool, guarantee). */
    public static final long MAX_SUBSETS = 50_000;
    /** Subsets inside one line: C(pick, guarantee). */
    public static final long MAX_PER_LINE = 5_000;
    private static final int CANDIDATES_PER_STEP = 40;
    private static final long SEED = 20260928L;

    private static final Map<String, List<List<Integer>>> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<List<Integer>>> eldest) {
            return size() > 64;
        }
    };

    private WheelGenerator() {
    }

    public static List<List<Integer>> generate(List<Integer> pool, int pick, int guarantee) {
        int v = pool.size();
        Checks.that(v <= MAX_POOL, "Wheel: at most " + MAX_POOL + " numbers.");
        Checks.that(guarantee >= 1 && guarantee <= pick, "Wheel: the guarantee is 1 to " + pick + " hits.");
        long subsets = Combinatorics.binomial(v, guarantee);
        Checks.that(subsets <= MAX_SUBSETS, "Wheel: C(" + v + "," + guarantee + ") = " + subsets + " combinations to cover -- at most "
                + MAX_SUBSETS + ". Use fewer numbers or a smaller guarantee.");
        Checks.that(Combinatorics.binomial(pick, guarantee) <= MAX_PER_LINE, "Wheel: the guarantee is too large for lines of " + pick + " numbers.");
        String key = pool + "|" + pick + "|" + guarantee;
        synchronized (CACHE) {
            List<List<Integer>> cached = CACHE.get(key);
            if (cached != null) return cached;
        }
        List<List<Integer>> out = search(pool, pick, guarantee);
        synchronized (CACHE) {
            CACHE.put(key, out);
        }
        return out;
    }

    private static List<List<Integer>> search(List<Integer> pool, int k, int t) {
        int v = pool.size();
        Set<Long> uncovered = new HashSet<>();
        List<Long> order = new ArrayList<>();
        subsets(v, t, 0, 0L, 0, s -> {
            uncovered.add(s);
            order.add(s);
        });
        List<Long> all = List.copyOf(order);
        Random rnd = new Random(SEED);
        List<Long> lines = new ArrayList<>();
        Set<Long> chosen = new HashSet<>();
        while (!uncovered.isEmpty()) {
            order.removeIf(s -> !uncovered.contains(s));
            long best = 0;
            int bestCount = -1;
            for (int c = 0; c < CANDIDATES_PER_STEP; c++) {
                // start from an uncovered subset, fill the line with random other numbers of the pool
                long line = order.get(rnd.nextInt(order.size()));
                while (Long.bitCount(line) < k) line |= 1L << rnd.nextInt(v);
                if (chosen.contains(line)) continue;
                int[] count = {0};
                long fixed = line;
                subsetsOf(fixed, t, s -> {
                    if (uncovered.contains(s)) count[0]++;
                });
                if (count[0] > bestCount) {
                    bestCount = count[0];
                    best = line;
                }
            }
            if (bestCount <= 0) continue;
            chosen.add(best);
            lines.add(best);
            subsetsOf(best, t, uncovered::remove);
        }
        for (long s : all) {
            boolean ok = false;
            for (long line : lines) if ((line & s) == s) { ok = true; break; }
            if (!ok) throw new IllegalStateException("Wheel: internal check failed.");
        }
        List<List<Integer>> out = new ArrayList<>();
        for (long line : lines) {
            List<Integer> nums = new ArrayList<>(k);
            for (int i = 0; i < v; i++) if ((line >>> i & 1) == 1) nums.add(pool.get(i));
            out.add(List.copyOf(nums));
        }
        return List.copyOf(out);
    }

    private interface Sink {
        void accept(long subset);
    }

    /** Every t-subset of {0..v-1} as a bit mask. */
    private static void subsets(int v, int t, int from, long mask, int size, Sink sink) {
        if (size == t) {
            sink.accept(mask);
            return;
        }
        for (int i = from; i <= v - (t - size); i++) subsets(v, t, i + 1, mask | 1L << i, size + 1, sink);
    }

    /** Every t-subset of the bits of {@code line}. */
    private static void subsetsOf(long line, int t, Sink sink) {
        int[] bits = new int[Long.bitCount(line)];
        for (int i = 0, j = 0; i < 64; i++) if ((line >>> i & 1) == 1) bits[j++] = i;
        pick(bits, t, 0, 0L, 0, sink);
    }

    private static void pick(int[] bits, int t, int from, long mask, int size, Sink sink) {
        if (size == t) {
            sink.accept(mask);
            return;
        }
        for (int i = from; i <= bits.length - (t - size); i++) pick(bits, t, i + 1, mask | 1L << bits[i], size + 1, sink);
    }
}
