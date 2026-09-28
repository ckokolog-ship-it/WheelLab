package io.wheellab.matchx;

import io.wheellab.core.Checks;
import io.wheellab.core.Histograms;
import io.wheellab.core.LineSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A MatchX ticket: {@code matches} matches split into groups, each group with its own system. The lines of
 * the ticket are every combination of one line per group (the last group changes fastest). A match that no
 * group plays shows {@link Symbols#NONE}.
 */
public final class Ticket {

    private final int matches;
    private final List<Group> groups;

    public Ticket(int matches, List<Group> groups) {
        Checks.that(matches >= 1 && matches <= Group.MAX_MATCHES, "A ticket has 1 to " + Group.MAX_MATCHES + " matches.");
        Checks.that(!groups.isEmpty(), "A ticket needs at least one group.");
        boolean[] used = new boolean[matches + 1];
        for (int g = 0; g < groups.size(); g++)
            for (int m : groups.get(g).matches()) {
                Checks.that(m >= 1 && m <= matches, "Group " + (g + 1) + ": match " + m + " is not on the ticket (1 to " + matches + ").");
                Checks.that(!used[m], "Match " + m + " is in two groups -- every match belongs to one group only.");
                used[m] = true;
            }
        this.matches = matches;
        this.groups = List.copyOf(groups);
    }

    public int matches() {
        return matches;
    }

    public List<Group> groups() {
        return groups;
    }

    /** Matches that no group plays (1-based). */
    public List<Integer> uncovered() {
        boolean[] used = new boolean[matches + 1];
        for (Group g : groups) for (int m : g.matches()) used[m] = true;
        List<Integer> out = new ArrayList<>();
        for (int m = 1; m <= matches; m++) if (!used[m]) out.add(m);
        return out;
    }

    public long count() {
        long total = 1;
        for (Group g : groups) total = Math.multiplyExact(total, g.count());
        return total;
    }

    public LineSource<String> lines() {
        List<LineSource<String>> parts = groups.stream().map(Group::lines).toList();
        long[] sizes = parts.stream().mapToLong(LineSource::size).toArray();
        long total = count();
        return new LineSource<>() {
            @Override
            public long size() {
                return total;
            }

            @Override
            public List<String> get(long index) {
                Checks.index(index, total);
                String[] line = new String[matches];
                Arrays.fill(line, Symbols.NONE);
                long rest = index;
                for (int g = parts.size() - 1; g >= 0; g--) {
                    List<String> part = parts.get(g).get(rest % sizes[g]);
                    rest /= sizes[g];
                    List<Integer> ms = groups.get(g).matches();
                    for (int i = 0; i < ms.size(); i++) line[ms.get(i) - 1] = part.get(i);
                }
                return Arrays.asList(line);
            }
        };
    }

    /** Hit histogram of the whole ticket: the groups' histograms combined (convolution). */
    public long[] histogram(List<String> result) {
        Checks.that(result.size() == matches, "The result needs one outcome per match (" + matches + ").");
        long[] hist = {1};
        for (Group g : groups) hist = Histograms.convolve(hist, groupHistogram(g, result));
        return Histograms.padded(hist, matches + 1);
    }

    public long[] groupHistogram(Group g, List<String> result) {
        List<String> local = new ArrayList<>();
        for (int m : g.matches()) local.add(result.get(m - 1));
        return g.histogram(local);
    }
}
