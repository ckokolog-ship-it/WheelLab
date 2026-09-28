package io.wheellab.pick;

import io.wheellab.core.Checks;
import io.wheellab.core.Combinatorics;
import io.wheellab.core.LineSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * One entry of a slip: its main part (single line, system, groups or wheel) and, in games with a bonus pool,
 * its bonus numbers. A system entry takes a bonus <em>pool</em> (every combination of the bonus pick); the
 * other entries take exactly the bonus pick. Lines list the main numbers, then the bonus numbers written
 * {@code +n}.
 */
public final class Entry {

    private final PickGame game;
    private final MainPart main;
    private final List<Integer> bonus;
    private final long bonusCount;

    public Entry(PickGame game, MainPart main, List<Integer> bonusRaw) {
        this.game = game;
        this.main = main;
        if (!game.hasBonus()) {
            Checks.that(bonusRaw == null || bonusRaw.isEmpty(), "This game has no bonus numbers.");
            bonus = List.of();
            bonusCount = 1;
            return;
        }
        String label = game.bonus().label();
        TreeSet<Integer> set = new TreeSet<>();
        for (int n : bonusRaw == null ? List.<Integer>of() : bonusRaw) {
            game.bonusNumber(n, label);
            Checks.that(set.add(n), label + ": " + n + " is chosen twice.");
        }
        bonus = List.copyOf(set);
        int bp = game.bonusPick();
        if (main instanceof Parts.Pool) {
            Checks.that(bonus.size() >= bp, label + ": choose at least " + bp + " numbers (a system plays every combination of them).");
            bonusCount = Combinatorics.binomial(bonus.size(), bp);
        } else {
            Checks.that(bonus.size() == bp, label + ": choose exactly " + bp + ".");
            bonusCount = 1;
        }
    }

    public MainPart main() {
        return main;
    }

    public List<Integer> bonus() {
        return bonus;
    }

    public String type() {
        return main.type();
    }

    public long bonusCount() {
        return bonusCount;
    }

    public long count() {
        return Math.multiplyExact(main.count(), bonusCount);
    }

    public LineSource<String> lines() {
        long total = count();
        int bp = game.bonusPick();
        return new LineSource<>() {
            @Override
            public long size() {
                return total;
            }

            @Override
            public List<String> get(long index) {
                Checks.index(index, total);
                List<String> out = new ArrayList<>();
                for (int n : main.line(index / bonusCount)) out.add(String.valueOf(n));
                if (bp > 0) for (int n : Combinatorics.combinationAt(bonus, bp, index % bonusCount)) out.add("+" + n);
                return out;
            }
        };
    }

    /** {@code grid[h][s]} = lines with h main hits and s bonus hits. */
    public long[][] histogram(Set<Integer> drawn, Set<Integer> drawnBonus) {
        long[] mainHist = main.histogram(drawn);
        int bp = game.bonusPick();
        long[] bonusHist = new long[bp + 1];
        if (bp == 0) {
            bonusHist[0] = 1;
        } else {
            int a = MainPart.hits(bonus, drawnBonus), b = bonus.size() - a;
            for (int s = 0; s <= bp; s++) bonusHist[s] = Math.multiplyExact(Combinatorics.binomial(a, s), Combinatorics.binomial(b, bp - s));
        }
        long[][] grid = new long[mainHist.length][bp + 1];
        for (int h = 0; h < mainHist.length; h++)
            for (int s = 0; s <= bp; s++) grid[h][s] = Math.multiplyExact(mainHist[h], bonusHist[s]);
        return grid;
    }

    /** How many lines contain each main number (index n - min). */
    public long[] frequency() {
        long[] f = main.frequency();
        for (int i = 0; i < f.length; i++) f[i] = Math.multiplyExact(f[i], bonusCount);
        return f;
    }

    /** How many lines contain each bonus number (index n - bonus min). */
    public long[] bonusFrequency() {
        if (!game.hasBonus()) return new long[0];
        long[] f = new long[game.bonus().size()];
        long each = Math.multiplyExact(main.count(), Combinatorics.binomial(bonus.size() - 1, game.bonusPick() - 1));
        for (int n : bonus) f[n - game.bonus().min()] = each;
        return f;
    }
}
