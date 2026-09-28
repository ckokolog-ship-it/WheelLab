package io.wheellab.matchx;

import io.wheellab.core.LineSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Every group type against a plain enumeration of all combinations of the picks: the same set of lines
 * (no duplicates), the same count, and the same hit histogram for random results.
 */
class MatchXBruteForceTest {

    private static final Random RND = new Random(7);

    private static List<List<String>> randomPicks(int n) {
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<String> fam = new ArrayList<>(Symbols.FAMILIES.get(RND.nextInt(Symbols.FAMILIES.size())));
            java.util.Collections.shuffle(fam, RND);
            out.add(List.copyOf(fam.subList(0, 1 + RND.nextInt(fam.size()))));
        }
        return out;
    }

    private static List<List<String>> allCombinations(List<List<String>> picks) {
        List<List<String>> out = new ArrayList<>();
        out.add(List.of());
        for (List<String> p : picks) {
            List<List<String>> next = new ArrayList<>();
            for (List<String> prefix : out)
                for (String s : p) {
                    List<String> l = new ArrayList<>(prefix);
                    l.add(s);
                    next.add(l);
                }
            out = next;
        }
        return out;
    }

    private static List<String> randomResult(List<List<String>> picks) {
        List<String> r = new ArrayList<>();
        for (List<String> p : picks) {
            List<String> fam = Symbols.familyOf(p.get(0));
            r.add(fam.get(RND.nextInt(fam.size())));
        }
        return r;
    }

    private static List<Integer> positions(int n) {
        List<Integer> out = new ArrayList<>();
        for (int i = 1; i <= n; i++) out.add(i);
        return out;
    }

    private static void assertSame(Group g, List<List<String>> expected, List<List<String>> picks) {
        LineSource<String> lines = g.lines();
        assertEquals(expected.size(), g.count(), g.type() + " count");
        assertEquals(expected.size(), lines.size(), g.type() + " size");
        Set<List<String>> got = new HashSet<>();
        for (long i = 0; i < lines.size(); i++) assertTrue(got.add(lines.get(i)), g.type() + " duplicate line " + lines.get(i));
        assertEquals(new HashSet<>(expected), got, g.type() + " lines");
        for (int t = 0; t < 5; t++) {
            List<String> result = randomResult(picks);
            long[] hist = new long[picks.size() + 1];
            for (List<String> l : expected) {
                int h = 0;
                for (int i = 0; i < l.size(); i++) if (l.get(i).equals(result.get(i))) h++;
                hist[h]++;
            }
            assertArrayEquals(hist, g.histogram(result), g.type() + " histogram for " + result);
        }
    }

    @Test
    void fullGroup() {
        for (int it = 0; it < 40; it++) {
            List<List<String>> picks = randomPicks(1 + RND.nextInt(7));
            assertSame(new FullGroup(positions(picks.size()), picks), allCombinations(picks), picks);
        }
    }

    @Test
    void errorsGroup() {
        for (int it = 0; it < 60; it++) {
            List<List<String>> picks = randomPicks(1 + RND.nextInt(7));
            List<Integer> errors = new ArrayList<>();
            for (int e = 0; e <= picks.size(); e++) if (RND.nextBoolean()) errors.add(e);
            if (errors.isEmpty()) errors.add(0);
            List<List<String>> expected = new ArrayList<>();
            for (List<String> l : allCombinations(picks)) {
                int e = 0;
                for (int i = 0; i < l.size(); i++) if (!l.get(i).equals(picks.get(i).get(0))) e++;
                if (errors.contains(e)) expected.add(l);
            }
            assertSame(new ErrorsGroup(positions(picks.size()), picks, errors), expected, picks);
        }
    }

    @Test
    void rangesGroup() {
        for (int it = 0; it < 60; it++) {
            List<List<String>> picks = randomPicks(1 + RND.nextInt(7));
            int n = picks.size();
            int[][] ranges = new int[3][];
            for (int r = 0; r < 3; r++) {
                int lo = RND.nextInt(n + 1), hi = lo + RND.nextInt(n + 1 - lo);
                ranges[r] = new int[]{RND.nextInt(3) == 0 ? lo : 0, hi};
            }
            List<List<String>> expected = new ArrayList<>();
            for (List<String> l : allCombinations(picks)) {
                int[] c = new int[3];
                for (int i = 0; i < l.size(); i++) c[picks.get(i).indexOf(l.get(i))]++;
                boolean ok = true;
                for (int r = 0; r < 3; r++) ok &= c[r] >= ranges[r][0] && c[r] <= ranges[r][1];
                if (ok) expected.add(l);
            }
            assertSame(new RangesGroup(positions(n), picks, ranges), expected, picks);
        }
    }

    @Test
    void coveringGroupKeepsItsGuarantee() {
        for (int it = 0; it < 25; it++) {
            List<List<String>> picks = randomPicks(2 + RND.nextInt(5));
            int n = picks.size();
            int radius = RND.nextInt(n + 1), guarantee = 1 + RND.nextInt(n);
            CoveringGroup g = new CoveringGroup(positions(n), picks, radius, guarantee);
            List<List<String>> lines = new ArrayList<>();
            for (long i = 0; i < g.lines().size(); i++) lines.add(g.lines().get(i));
            assertEquals(lines.size(), new HashSet<>(lines).size(), "no duplicate lines");
            for (List<String> result : allCombinations(picks)) {
                int changes = 0;
                for (int i = 0; i < n; i++) if (!result.get(i).equals(picks.get(i).get(0))) changes++;
                if (changes > radius) continue;
                int best = 0;
                for (List<String> l : lines) {
                    int h = 0;
                    for (int i = 0; i < n; i++) if (l.get(i).equals(result.get(i))) h++;
                    best = Math.max(best, h);
                }
                assertTrue(best >= guarantee, "result " + result + " gets only " + best + " hits");
            }
            // same input -> same lines (count and build must agree)
            assertEquals(lines, allLines(new CoveringGroup(positions(n), picks, radius, guarantee)));
        }
    }

    private static List<List<String>> allLines(Group g) {
        List<List<String>> out = new ArrayList<>();
        for (long i = 0; i < g.lines().size(); i++) out.add(g.lines().get(i));
        return out;
    }

    @Test
    void ticketCombinesGroupsOnTheirMatches() {
        for (int it = 0; it < 30; it++) {
            int n = 3 + RND.nextInt(5);
            List<Integer> order = positions(n);
            java.util.Collections.shuffle(order, RND);
            List<Group> groups = new ArrayList<>();
            List<List<List<String>>> groupLines = new ArrayList<>();
            int at = 0;
            while (at < n - 1) { // leave the last shuffled match unplayed
                int size = 1 + RND.nextInt(Math.min(3, n - 1 - at));
                List<Integer> ms = new ArrayList<>(order.subList(at, at + size));
                ms.sort(null);
                at += size;
                List<List<String>> picks = randomPicks(size);
                Group g = RND.nextBoolean() ? new FullGroup(ms, picks) : new ErrorsGroup(ms, picks, List.of(0, 1));
                groups.add(g);
                groupLines.add(allLines(g));
            }
            Ticket t = new Ticket(n, groups);
            assertEquals(List.of(order.get(n - 1)), t.uncovered());
            Set<List<String>> expected = new HashSet<>();
            expected.add(new ArrayList<>(java.util.Collections.nCopies(n, Symbols.NONE)));
            for (int g = 0; g < groups.size(); g++) {
                Set<List<String>> next = new HashSet<>();
                for (List<String> base : expected)
                    for (List<String> gl : groupLines.get(g)) {
                        List<String> l = new ArrayList<>(base);
                        for (int i = 0; i < gl.size(); i++) l.set(groups.get(g).matches().get(i) - 1, gl.get(i));
                        next.add(l);
                    }
                expected = next;
            }
            Set<List<String>> got = new HashSet<>();
            for (long i = 0; i < t.lines().size(); i++) got.add(t.lines().get(i));
            assertEquals(expected.size(), t.count());
            assertEquals(expected, got);
            List<String> result = new ArrayList<>();
            for (int m = 0; m < n; m++) result.add(List.of("1", "X", "2", "U", "O", "GG", "NG").get(RND.nextInt(7)));
            long[] hist = new long[n + 1];
            for (List<String> l : expected) {
                int h = 0;
                for (int i = 0; i < n; i++) if (l.get(i).equals(result.get(i))) h++;
                hist[h]++;
            }
            assertArrayEquals(hist, t.histogram(result));
        }
    }

    @Test
    void clearErrors() {
        assertThrows(IllegalArgumentException.class, () -> Symbols.pick(List.of("1", "U"), 3));
        assertThrows(IllegalArgumentException.class, () -> Symbols.pick(List.of("1", "1"), 3));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new Ticket(4, List.of(new FullGroup(List.of(1, 2), List.of(List.of("1"), List.of("X"))),
                        new FullGroup(List.of(2, 3), List.of(List.of("1"), List.of("X"))))));
        assertTrue(e.getMessage().contains("two groups"), e.getMessage());
    }
}
