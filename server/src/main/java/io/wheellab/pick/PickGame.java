package io.wheellab.pick;

import io.wheellab.core.Checks;

/**
 * A number game: lines of {@code pick} numbers from 1..{@code numbers}; {@code draw} numbers are drawn.
 * LOTTO is 6 from 49 with 6 drawn; KENO is 1-12 spots from 80 with 20 drawn.
 */
public record PickGame(int numbers, int pick, int draw) {

    public static final int MAX_NUMBERS = 100;
    public static final int MAX_PICK = 20;

    public PickGame {
        Checks.that(numbers >= 2 && numbers <= MAX_NUMBERS, "The numbers go from 1 to N, with N from 2 to " + MAX_NUMBERS + ".");
        Checks.that(pick >= 1 && pick <= Math.min(MAX_PICK, numbers), "A line has 1 to " + Math.min(MAX_PICK, numbers) + " numbers.");
        Checks.that(draw >= 1 && draw <= numbers, "1 to " + numbers + " numbers are drawn.");
    }

    /** Checks that {@code n} is one of the game's numbers. */
    public void number(int n, String where) {
        Checks.that(n >= 1 && n <= numbers, where + ": " + n + " is not a number of the game (1 to " + numbers + ").");
    }
}
