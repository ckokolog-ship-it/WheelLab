package io.wheellab.pick;

import io.wheellab.core.Combinatorics;
import io.wheellab.core.LineSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Every entry type against plain enumeration: lines, counts and hit histograms; the wheel guarantee; ranks. */
class PickBruteForceTest {

    private static final Random RND = new Random(11);

    private static List<Integer> sample(List<Integer> from, int n) {
        List<Integer> copy = new ArrayList<>(from);
        Collections.shuffle(copy, RND);
        return new ArrayList<>(copy.subList(0, n));
    }

    private static List<Integer> numbers(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(i);
        return out;
    }

    /** All k-subsets of items, each sorted. */
    private static List<List<Integer>> subsets(List<Integer> items, int k) {
        List<List<Integer>> out = new ArrayList<>();
        List<Integer> sorted = new ArrayList<>(items);
        sorted.sort(null);
        rec(sorted, k, 0, new ArrayList<>(), out);
        return out;
    }

    private static void rec(List<Integer> items, int k, int from, List<Integer> cur, List<List<Integer>> out) {
        if (cur.size() == k) {
            out.add(new ArrayList<>(cur));
            return;
        }
        for (int i = from; i < items.size(); i++) {
            cur.add(items.get(i));
            rec(items, k, i + 1, cur, out);
            cur.remove(cur.size() - 1);
        }
    }

    private static List<List<Integer>> lines(MainPart e) {
        List<List<Integer>> out = new ArrayList<>();
        for (long i = 0; i < e.count(); i++) out.add(e.line(i));
        return out;
    }

    private static void assertMatches(MainPart e, PickGame game, List<List<Integer>> expected) {
        List<List<Integer>> got = lines(e);
        assertEquals(expected.size(), e.count(), e.type() + " count");
        assertEquals(new HashSet<>(expected), new HashSet<>(got), e.type() + " lines");
        assertEquals(got.size(), new HashSet<>(got).size(), e.type() + " duplicates");
        for (List<Integer> l : got) {
            List<Integer> s = new ArrayList<>(l);
            s.sort(null);
            assertEquals(s, l, "lines are sorted");
        }
        for (int t = 0; t < 4; t++) {
            Set<Integer> drawn = new HashSet<>(sample(numbers(game.max()), game.draw()));
            long[] hist = new long[game.pick() + 1];
            for (List<Integer> l : expected) hist[(int) l.stream().filter(drawn::contains).count()]++;
            assertArrayEquals(hist, e.histogram(drawn), e.type() + " histogram");
        }
        long[] freq = new long[game.size()];
        for (List<Integer> l : expected) for (int n : l) freq[n - game.min()]++;
        assertArrayEquals(freq, e.frequency(), e.type() + " frequency");
    }

    private static PickGame randomGame() {
        int n = 10 + RND.nextInt(20);
        int k = 2 + RND.nextInt(4);
        return new PickGame(n, k, RND.nextBoolean() ? k : Math.min(n, k + RND.nextInt(10)));
    }

    @Test
    void systems() {
        for (int it = 0; it < 30; it++) {
            PickGame game = randomGame();
            List<Integer> pool = sample(numbers(game.max()), game.pick() + RND.nextInt(4));
            assertMatches(new Parts.Pool(game, pool), game, subsets(pool, game.pick()));
            List<Integer> single = sample(numbers(game.max()), game.pick());
            assertMatches(new Parts.Single(game, single), game, subsets(single, game.pick()));
        }
    }

    @Test
    void groups() {
        for (int it = 0; it < 30; it++) {
            PickGame game = randomGame();
            List<Integer> all = sample(numbers(game.max()), game.max());
            List<List<Integer>> groups = new ArrayList<>();
            List<Integer> take = new ArrayList<>();
            int left = game.pick(), at = 0;
            while (left > 0) {
                int t = 1 + RND.nextInt(left);
                int size = Math.min(t + RND.nextInt(3), all.size() - at - (left - t));
                groups.add(new ArrayList<>(all.subList(at, at + size)));
                take.add(t);
                at += size;
                left -= t;
            }
            List<List<Integer>> expected = new ArrayList<>();
            expected.add(List.of());
            for (int g = 0; g < groups.size(); g++) {
                List<List<Integer>> next = new ArrayList<>();
                for (List<Integer> base : expected)
                    for (List<Integer> part : subsets(groups.get(g), take.get(g))) {
                        List<Integer> l = new ArrayList<>(base);
                        l.addAll(part);
                        l.sort(null);
                        next.add(l);
                    }
                expected = next;
            }
            assertMatches(new Parts.Groups(game, groups, take), game, expected);
        }
    }

    @Test
    void wheelKeepsItsGuarantee() {
        for (int it = 0; it < 25; it++) {
            PickGame game = randomGame();
            List<Integer> pool = sample(numbers(game.max()), Math.min(game.max(), game.pick() + 1 + RND.nextInt(6)));
            int t = 1 + RND.nextInt(game.pick());
            Parts.Wheel w = new Parts.Wheel(game, pool, t);
            List<List<Integer>> got = lines(w);
            assertEquals(got.size(), new HashSet<>(got).size(), "no duplicate lines");
            for (List<Integer> l : got) assertTrue(pool.containsAll(l) && l.size() == game.pick());
            for (List<Integer> s : subsets(pool, t)) {
                boolean covered = got.stream().anyMatch(l -> l.containsAll(s));
                assertTrue(covered, "subset " + s + " is not in any line");
            }
            assertTrue(got.size() <= Combinatorics.binomial(pool.size(), game.pick()));
            assertEquals(got, lines(new Parts.Wheel(game, pool, t)), "same input, same lines");
        }
    }

    @Test
    void rankIsTheInverseOfCombinationAt() {
        List<Integer> items = numbers(12);
        for (long i = 0; i < Combinatorics.binomial(12, 5); i++) {
            List<Integer> c = Combinatorics.combinationAt(items, 5, i);
            int[] pos = c.stream().mapToInt(x -> x - 1).toArray();
            assertEquals(i, Combinatorics.rank(12, pos));
        }
        // the last line of 6 from 49
        assertEquals(13_983_815, Combinatorics.rank(49, new int[]{43, 44, 45, 46, 47, 48}));
    }

    /** Entries with a bonus pool: lines (main then +bonus), 2-D hit grid and bonus frequency. */
    @Test
    void bonusPool() {
        for (int it = 0; it < 30; it++) {
            int bmax = 4 + RND.nextInt(8), bp = 1 + RND.nextInt(2);
            PickGame game = new PickGame("Test", 0, 15 + RND.nextInt(10), 2 + RND.nextInt(3), 3, new PickGame.Bonus("Star", 1, bmax, bp, bp));
            List<Integer> mains = new ArrayList<>();
            for (int n = game.min(); n <= game.max(); n++) mains.add(n);
            boolean system = RND.nextBoolean();
            MainPart part = system ? new Parts.Pool(game, sample(mains, game.pick() + RND.nextInt(3))) : new Parts.Single(game, sample(mains, game.pick()));
            List<Integer> bonus = sample(numbers(bmax), system ? bp + RND.nextInt(bmax - bp + 1) : bp);
            Entry e = new Entry(game, part, bonus);
            List<List<String>> expected = new ArrayList<>();
            for (List<Integer> m : lines(part))
                for (List<Integer> b : subsets(bonus, bp)) {
                    List<String> l = new ArrayList<>();
                    m.forEach(x -> l.add(String.valueOf(x)));
                    b.forEach(x -> l.add("+" + x));
                    expected.add(l);
                }
            LineSource<String> src = e.lines();
            List<List<String>> got = new ArrayList<>();
            for (long i = 0; i < src.size(); i++) got.add(src.get(i));
            assertEquals(expected, got, "lines, main slowest");
            Set<Integer> drawn = new HashSet<>(sample(mains, game.draw()));
            Set<Integer> drawnBonus = new HashSet<>(sample(numbers(bmax), bp));
            long[][] grid = new long[game.pick() + 1][bp + 1];
            long[] bf = new long[bmax];
            for (List<String> l : expected) {
                int h = 0, s = 0;
                for (String x : l) {
                    if (x.startsWith("+")) {
                        int n = Integer.parseInt(x.substring(1));
                        bf[n - 1]++;
                        if (drawnBonus.contains(n)) s++;
                    } else if (drawn.contains(Integer.parseInt(x))) h++;
                }
                grid[h][s]++;
            }
            long[][] g = e.histogram(drawn, drawnBonus);
            for (int h = 0; h <= game.pick(); h++) assertArrayEquals(grid[h], g[h], "grid row " + h);
            assertArrayEquals(bf, e.bonusFrequency(), "bonus frequency");
        }
        PickGame g = new PickGame("T", 1, 35, 5, 5, new PickGame.Bonus("Star", 1, 12, 2, 2));
        assertThrows(IllegalArgumentException.class, () -> new Entry(g, new Parts.Single(g, List.of(1, 2, 3, 4, 5)), List.of(1)));
        assertThrows(IllegalArgumentException.class, () -> new Entry(new PickGame(49, 6, 6), new Parts.Single(new PickGame(49, 6, 6), List.of(1, 2, 3, 4, 5, 6)), List.of(1)));
    }

    @Test
    void clearErrors() {
        PickGame lotto = new PickGame(49, 6, 6);
        assertThrows(IllegalArgumentException.class, () -> new Parts.Single(lotto, List.of(1, 2, 3)));
        assertThrows(IllegalArgumentException.class, () -> new Parts.Pool(lotto, List.of(1, 2, 3, 4, 5, 50)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Parts.Groups(lotto, List.of(List.of(1, 2, 3), List.of(3, 4, 5, 6)), List.of(2, 4)));
        assertTrue(e.getMessage().contains("two groups"), e.getMessage());
    }
}
