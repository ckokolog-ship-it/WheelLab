package io.wheellab.core;

/** Small argument checks shared by the engines. Messages are meant to be shown to the user as-is. */
public final class Checks {

    private Checks() {
    }

    public static void index(long index, long size) {
        if (index < 0 || index >= size) throw new IndexOutOfBoundsException("index " + index + " outside [0, " + size + ")");
    }

    public static void that(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
