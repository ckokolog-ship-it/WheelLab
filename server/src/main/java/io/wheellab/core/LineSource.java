package io.wheellab.core;

import java.util.List;

/**
 * A lazily indexed list of lines (columns). Nothing is materialized: {@link #get(long)} computes the
 * line at a position on demand, so a system with billions of lines costs no memory until a page of it
 * is read.
 *
 * @param <T> the element type of a line (a symbol string, a number, ...)
 */
public interface LineSource<T> {

    long size();

    List<T> get(long index);

    /** The lines of {@code parts} one after another. */
    static <T> LineSource<T> concat(List<? extends LineSource<T>> parts) {
        long[] starts = new long[parts.size() + 1];
        for (int i = 0; i < parts.size(); i++) starts[i + 1] = Math.addExact(starts[i], parts.get(i).size());
        return new LineSource<>() {
            @Override
            public long size() {
                return starts[parts.size()];
            }

            @Override
            public List<T> get(long index) {
                Checks.index(index, size());
                int lo = 0, hi = parts.size() - 1;
                while (lo < hi) {
                    int mid = (lo + hi + 1) >>> 1;
                    if (starts[mid] <= index) lo = mid;
                    else hi = mid - 1;
                }
                return parts.get(lo).get(index - starts[lo]);
            }
        };
    }

    /** A fixed, already built list of lines. */
    static <T> LineSource<T> of(List<List<T>> lines) {
        return new LineSource<>() {
            @Override
            public long size() {
                return lines.size();
            }

            @Override
            public List<T> get(long index) {
                Checks.index(index, size());
                return lines.get((int) index);
            }
        };
    }
}
