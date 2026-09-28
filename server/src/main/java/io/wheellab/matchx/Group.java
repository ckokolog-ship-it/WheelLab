package io.wheellab.matchx;

import io.wheellab.core.Checks;
import io.wheellab.core.LineSource;

import java.util.List;

/**
 * A group of matches of a MatchX ticket, played with one system. A ticket's lines are every combination of
 * one line from each of its groups (the groups never share a match).
 * <p>
 * {@code picks.get(i)} are the outcomes played on match {@code matches.get(i)}; a group line lists one
 * outcome per match of the group, in the same order.
 */
public abstract class Group {

    /** Upper bound for the matches of a ticket (and so of a group). */
    public static final int MAX_MATCHES = 30;

    protected final List<Integer> matches;
    protected final List<List<String>> picks;

    protected Group(List<Integer> matches, List<List<String>> picks) {
        Checks.that(!matches.isEmpty(), "A group needs at least one match.");
        Checks.that(matches.size() == picks.size(), "Each match of a group needs its outcomes.");
        Checks.that(matches.size() <= MAX_MATCHES, "A group has at most " + MAX_MATCHES + " matches.");
        this.matches = List.copyOf(matches);
        this.picks = picks.stream().map(List::copyOf).toList();
    }

    public List<Integer> matches() {
        return matches;
    }

    /** "full", "errors", "ranges" or "covering". */
    public abstract String type();

    /** The lines of the group, lazily. */
    public abstract LineSource<String> lines();

    /** How many lines the group has. */
    public long count() {
        return lines().size();
    }

    /**
     * {@code hist[h]} = how many lines of the group have exactly {@code h} outcomes equal to {@code result}
     * ({@code result.get(i)} is the outcome of match {@code matches.get(i)}).
     */
    public abstract long[] histogram(List<String> result);

    /** Histogram by looking at every line -- for groups that hold few lines, and for tests. */
    protected long[] histogramByLines(List<String> result) {
        long[] hist = new long[matches.size() + 1];
        LineSource<String> lines = lines();
        for (long i = 0; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            int hits = 0;
            for (int j = 0; j < line.size(); j++) if (line.get(j).equals(result.get(j))) hits++;
            hist[hits]++;
        }
        return hist;
    }
}
