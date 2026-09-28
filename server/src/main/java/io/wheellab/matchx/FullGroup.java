package io.wheellab.matchx;

import io.wheellab.core.Checks;
import io.wheellab.core.LineSource;

import java.util.Arrays;
import java.util.List;

/**
 * Full system: every combination of the outcomes chosen on each match. Lines are in mixed-radix order --
 * the last match changes fastest, each match in the order of its picks.
 */
public final class FullGroup extends Group {

    public FullGroup(List<Integer> matches, List<List<String>> picks) {
        super(matches, picks);
    }

    @Override
    public String type() {
        return "full";
    }

    @Override
    public long count() {
        long total = 1;
        for (List<String> p : picks) total = Math.multiplyExact(total, p.size());
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
                String[] line = new String[picks.size()];
                long rest = index;
                for (int i = picks.size() - 1; i >= 0; i--) {
                    List<String> p = picks.get(i);
                    line[i] = p.get((int) (rest % p.size()));
                    rest /= p.size();
                }
                return Arrays.asList(line);
            }
        };
    }

    /** Closed form: a match adds a hit on one of its picks when the result is among them. */
    @Override
    public long[] histogram(List<String> result) {
        long[] hist = {1};
        for (int i = 0; i < picks.size(); i++) {
            int n = picks.get(i).size();
            hist = Histograms.convolve(hist, picks.get(i).contains(result.get(i)) ? new long[]{n - 1, 1} : new long[]{n});
        }
        return Histograms.padded(hist, picks.size() + 1);
    }
}
