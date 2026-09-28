package io.wheellab.pick;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** The main numbers of an entry -- a single line, a system, groups or a wheel. Lines are sorted ascending. */
public abstract class MainPart {

    protected final PickGame game;

    protected MainPart(PickGame game) {
        this.game = game;
    }

    /** "single", "system", "groups" or "wheel". */
    public abstract String type();

    public abstract long count();

    /** The {@code index}-th line (0-based), sorted. */
    public abstract List<Integer> line(long index);

    /** {@code hist[h]} = lines with exactly {@code h} of their numbers among {@code drawn}. */
    public abstract long[] histogram(Set<Integer> drawn);

    /** How many lines contain each number: {@code counts[n - game.min()]}. */
    public abstract long[] frequency();

    static int hits(Collection<Integer> numbers, Set<Integer> drawn) {
        int h = 0;
        for (int n : numbers) if (drawn.contains(n)) h++;
        return h;
    }
}
