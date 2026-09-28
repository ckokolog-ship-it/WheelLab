package io.wheellab.matchx;

import io.wheellab.core.LineSource;

import java.util.List;

/**
 * Covering (reduced) system -- see {@link CoveringGenerator}: the first pick of every match is the
 * prediction; if the results differ from it on at most {@code radius} matches, at least one line has at
 * least {@code guarantee} hits.
 */
public final class CoveringGroup extends Group {

    private final int radius;
    private final int guarantee;
    private final List<List<String>> rows;

    public CoveringGroup(List<Integer> matches, List<List<String>> picks, int radius, int guarantee) {
        super(matches, picks);
        this.radius = radius;
        this.guarantee = guarantee;
        this.rows = CoveringGenerator.generate(this.picks, radius, guarantee);
    }

    @Override
    public String type() {
        return "covering";
    }

    public int radius() {
        return radius;
    }

    public int guarantee() {
        return guarantee;
    }

    @Override
    public LineSource<String> lines() {
        return LineSource.of(rows);
    }

    @Override
    public long[] histogram(List<String> result) {
        return histogramByLines(result);
    }
}
