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

    private static List<List<Integer>> lines(Entry e) {
        LineSource<String> src = e.lines();
        List<List<Integer>> out = new ArrayList<>();
        for (long i = 0; i < src.size(); i++) out.add(src.get(i).stream().map(Integer::parseInt).toList());
        return out;
    }

    private static void assertMatches(Entry e, PickGame game, List<List<Integer>> expected) {
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
            Set<Integer> drawn = new HashSet<>(sample(numbers(game.numbers()), game.draw()));
            long[] hist = new long[game.pick() + 1];
            for (List<Integer> l : expected) hist[(int) l.stream().filter(drawn::contains).count()]++;
            assertArrayEquals(hist, e.histogram(drawn), e.type() + " histogram");
        }
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
            List<Integer> pool = sample(numbers(game.numbers()), game.pick() + RND.nextInt(4));
            assertMatches(new Entries.Pool(game, pool), game, subsets(pool, game.pick()));
            List<Integer> single = sample(numbers(game.numbers()), game.pick());
            assertMatches(new Entries.Single(game, single), game, subsets(single, game.pick()));
        }
    }

    @Test
    void groups() {
        for (int it = 0; it < 30; it++) {
            PickGame game = randomGame();
            List<Integer> all = sample(numbers(game.numbers()), game.numbers());
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
            assertMatches(new Entries.Groups(game, groups, take), game, expected);
        }
    }

    @Test
    void wheelKeepsItsGuarantee() {
        for (int it = 0; it < 25; it++) {
            PickGame game = randomGame();
            List<Integer> pool = sample(numbers(game.numbers()), Math.min(game.numbers(), game.pick() + 1 + RND.nextInt(6)));
            int t = 1 + RND.nextInt(game.pick());
            Entries.Wheel w = new Entries.Wheel(game, pool, t);
            List<List<Integer>> got = lines(w);
            assertEquals(got.size(), new HashSet<>(got).size(), "no duplicate lines");
            for (List<Integer> l : got) assertTrue(pool.containsAll(l) && l.size() == game.pick());
            for (List<Integer> s : subsets(pool, t)) {
                boolean covered = got.stream().anyMatch(l -> l.containsAll(s));
                assertTrue(covered, "subset " + s + " is not in any line");
            }
            assertTrue(got.size() <= Combinatorics.binomial(pool.size(), game.pick()));
            assertEquals(got, lines(new Entries.Wheel(game, pool, t)), "same input, same lines");
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

    @Test
    void clearErrors() {
        PickGame lotto = new PickGame(49, 6, 6);
        assertThrows(IllegalArgumentException.class, () -> new Entries.Single(lotto, List.of(1, 2, 3)));
        assertThrows(IllegalArgumentException.class, () -> new Entries.Pool(lotto, List.of(1, 2, 3, 4, 5, 50)));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Entries.Groups(lotto, List.of(List.of(1, 2, 3), List.of(3, 4, 5, 6)), List.of(2, 4)));
        assertTrue(e.getMessage().contains("two groups"), e.getMessage());
    }
}
