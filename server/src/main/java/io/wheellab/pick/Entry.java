package io.wheellab.pick;

import io.wheellab.core.LineSource;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** One entry of a number-game slip -- a single line, a system, groups or a wheel. Lines are sorted ascending. */
public abstract class Entry {

    protected final PickGame game;

    protected Entry(PickGame game) {
        this.game = game;
    }

    /** "single", "system", "groups" or "wheel". */
    public abstract String type();

    public abstract long count();

    /** The lines, lazily -- each a list of numbers as text (for the paged line API). */
    public abstract LineSource<String> lines();

    /** {@code hist[h]} = lines with exactly {@code h} of their numbers among {@code drawn}. */
    public abstract long[] histogram(Set<Integer> drawn);

    static int hits(Collection<Integer> numbers, Set<Integer> drawn) {
        int h = 0;
        for (int n : numbers) if (drawn.contains(n)) h++;
        return h;
    }

    static List<String> text(List<Integer> sorted) {
        return sorted.stream().map(String::valueOf).toList();
    }
}
