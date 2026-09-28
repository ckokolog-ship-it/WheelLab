package io.wheellab.core;

/** Hit histograms: {@code hist[h]} = number of lines with exactly {@code h} hits. */
public final class Histograms {

    private Histograms() {
    }

    /** The histogram of the combination of two independent parts (hits add up). */
    public static long[] convolve(long[] a, long[] b) {
        long[] out = new long[a.length + b.length - 1];
        for (int i = 0; i < a.length; i++) {
            if (a[i] == 0) continue;
            for (int j = 0; j < b.length; j++) out[i + j] = Math.addExact(out[i + j], Math.multiplyExact(a[i], b[j]));
        }
        return out;
    }

    public static long[] padded(long[] hist, int length) {
        long[] out = new long[length];
        System.arraycopy(hist, 0, out, 0, Math.min(length, hist.length));
        return out;
    }

    public static long[] add(long[] a, long[] b) {
        long[] out = new long[Math.max(a.length, b.length)];
        for (int i = 0; i < out.length; i++)
            out[i] = Math.addExact(i < a.length ? a[i] : 0, i < b.length ? b[i] : 0);
        return out;
    }
}
