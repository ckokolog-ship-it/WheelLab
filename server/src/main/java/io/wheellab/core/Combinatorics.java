package io.wheellab.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/** Binomial coefficients and the combinatorial number system (k-subsets in lexicographic order). */
public final class Combinatorics {

    private Combinatorics() {
    }

    /** C(n, k) exactly; 0 when k is outside 0..n. Throws ArithmeticException above Long.MAX_VALUE. */
    public static long binomial(int n, int k) {
        if (k < 0 || n < 0 || k > n) return 0;
        k = Math.min(k, n - k);
        BigInteger r = BigInteger.ONE;
        for (int i = 0; i < k; i++) r = r.multiply(BigInteger.valueOf(n - i)).divide(BigInteger.valueOf(i + 1));
        return r.longValueExact();
    }

    /** The {@code index}-th (0-based) k-subset of {@code items}, in lexicographic order of positions. */
    public static <T> List<T> combinationAt(List<T> items, int k, long index) {
        int n = items.size();
        List<T> out = new ArrayList<>(k);
        long rest = index;
        int start = 0;
        for (int picked = 0; picked < k; picked++) {
            for (int i = start; i < n; i++) {
                long block = binomial(n - i - 1, k - picked - 1);
                if (rest < block) {
                    out.add(items.get(i));
                    start = i + 1;
                    break;
                }
                rest -= block;
            }
        }
        return out;
    }

    /** Inverse of {@link #combinationAt}: the 0-based rank of the increasing positions {@code pos} among the k-subsets of n. */
    public static long rank(int n, int[] pos) {
        int k = pos.length;
        long r = 0;
        int prev = -1;
        for (int i = 0; i < k; i++) {
            for (int j = prev + 1; j < pos[i]; j++) r = Math.addExact(r, binomial(n - j - 1, k - i - 1));
            prev = pos[i];
        }
        return r;
    }
}
