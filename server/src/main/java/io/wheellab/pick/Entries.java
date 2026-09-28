package io.wheellab.pick;

import io.wheellab.core.Checks;
import io.wheellab.core.Combinatorics;
import io.wheellab.core.LineSource;
import io.wheellab.core.Histograms;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** The entry types of a number-game slip. */
public final class Entries {

    private Entries() {
    }

    /** Distinct game numbers, sorted. */
    static List<Integer> numbers(PickGame game, List<Integer> raw, String where) {
        Checks.that(raw != null && !raw.isEmpty(), where + ": choose some numbers.");
        TreeSet<Integer> set = new TreeSet<>();
        for (int n : raw) {
            game.number(n, where);
            Checks.that(set.add(n), where + ": " + n + " is chosen twice.");
        }
        return List.copyOf(set);
    }

    /** One line of exactly {@code pick} numbers. */
    public static final class Single extends Entry {
        private final List<Integer> numbers;

        public Single(PickGame game, List<Integer> raw) {
            super(game);
            numbers = Entries.numbers(game, raw, "Single line");
            Checks.that(numbers.size() == game.pick(), "Single line: exactly " + game.pick() + " numbers.");
        }

        @Override
        public String type() {
            return "single";
        }

        @Override
        public long count() {
            return 1;
        }

        @Override
        public LineSource<String> lines() {
            return LineSource.of(List.of(text(numbers)));
        }

        @Override
        public long[] histogram(Set<Integer> drawn) {
            long[] h = new long[game.pick() + 1];
            h[hits(numbers, drawn)] = 1;
            return h;
        }
    }

    /** Full system: every combination of {@code pick} numbers from the chosen pool. */
    public static final class Pool extends Entry {
        private final List<Integer> pool;

        public Pool(PickGame game, List<Integer> raw) {
            super(game);
            pool = Entries.numbers(game, raw, "System");
            Checks.that(pool.size() >= game.pick(), "System: at least " + game.pick() + " numbers.");
        }

        public List<Integer> pool() {
            return pool;
        }

        @Override
        public String type() {
            return "system";
        }

        @Override
        public long count() {
            return Combinatorics.binomial(pool.size(), game.pick());
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
                    return text(Combinatorics.combinationAt(pool, game.pick(), index));
                }
            };
        }

        /** Closed form: C(a, h) × C(b, k - h), a = pool numbers drawn, b = the others. */
        @Override
        public long[] histogram(Set<Integer> drawn) {
            int a = hits(pool, drawn), b = pool.size() - a, k = game.pick();
            long[] h = new long[k + 1];
            for (int i = 0; i <= k; i++) h[i] = Math.multiplyExact(Combinatorics.binomial(a, i), Combinatorics.binomial(b, k - i));
            return h;
        }
    }

    /** Groups ("A x B"): {@code take[i]} numbers from group i, every combination; groups share no number. */
    public static final class Groups extends Entry {
        private final List<List<Integer>> groups;
        private final int[] take;
        private final long[] sizes;

        public Groups(PickGame game, List<List<Integer>> raw, List<Integer> takeRaw) {
            super(game);
            Checks.that(raw != null && !raw.isEmpty() && takeRaw != null && raw.size() == takeRaw.size(),
                    "Groups: every group needs its numbers and how many to take.");
            groups = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            take = new int[raw.size()];
            sizes = new long[raw.size()];
            int sum = 0;
            for (int i = 0; i < raw.size(); i++) {
                String name = "Group " + (char) ('A' + i);
                List<Integer> g = Entries.numbers(game, raw.get(i), name);
                for (int n : g) Checks.that(seen.add(n), "Groups: " + n + " is in two groups -- a number belongs to one group only.");
                take[i] = takeRaw.get(i);
                Checks.that(take[i] >= 1 && take[i] <= g.size(), name + ": take 1 to " + g.size() + " numbers.");
                sum += take[i];
                groups.add(g);
                sizes[i] = Combinatorics.binomial(g.size(), take[i]);
            }
            Checks.that(sum == game.pick(), "Groups: the numbers taken must add up to " + game.pick() + " (now " + sum + ").");
        }

        @Override
        public String type() {
            return "groups";
        }

        @Override
        public long count() {
            long total = 1;
            for (long s : sizes) total = Math.multiplyExact(total, s);
            return total;
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
                    List<Integer> line = new ArrayList<>();
                    long rest = index;
                    for (int i = groups.size() - 1; i >= 0; i--) { // the last group changes fastest
                        line.addAll(Combinatorics.combinationAt(groups.get(i), take[i], rest % sizes[i]));
                        rest /= sizes[i];
                    }
                    line.sort(null);
                    return text(line);
                }
            };
        }

        @Override
        public long[] histogram(Set<Integer> drawn) {
            long[] hist = {1};
            for (int i = 0; i < groups.size(); i++) {
                int a = hits(groups.get(i), drawn), b = groups.get(i).size() - a;
                long[] g = new long[take[i] + 1];
                for (int h = 0; h <= take[i]; h++) g[h] = Math.multiplyExact(Combinatorics.binomial(a, h), Combinatorics.binomial(b, take[i] - h));
                hist = Histograms.convolve(hist, g);
            }
            return Histograms.padded(hist, game.pick() + 1);
        }
    }

    /** Wheel: a reduced system on the chosen pool -- see {@link WheelGenerator}. */
    public static final class Wheel extends Entry {
        private final List<Integer> pool;
        private final int guarantee;
        private final List<List<Integer>> rows;

        public Wheel(PickGame game, List<Integer> raw, int guarantee) {
            super(game);
            pool = Entries.numbers(game, raw, "Wheel");
            Checks.that(pool.size() >= game.pick(), "Wheel: at least " + game.pick() + " numbers.");
            this.guarantee = guarantee;
            rows = WheelGenerator.generate(pool, game.pick(), guarantee);
        }

        public int guarantee() {
            return guarantee;
        }

        @Override
        public String type() {
            return "wheel";
        }

        @Override
        public long count() {
            return rows.size();
        }

        @Override
        public LineSource<String> lines() {
            return LineSource.of(rows.stream().map(Entry::text).toList());
        }

        @Override
        public long[] histogram(Set<Integer> drawn) {
            long[] h = new long[game.pick() + 1];
            for (List<Integer> r : rows) h[hits(r, drawn)]++;
            return h;
        }
    }
}
