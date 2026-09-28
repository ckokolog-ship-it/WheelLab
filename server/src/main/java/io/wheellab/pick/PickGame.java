package io.wheellab.pick;

import io.wheellab.core.Checks;

/**
 * A number game: lines of {@code pick} numbers from {@code min}..{@code max}; {@code draw} numbers are drawn.
 * Optionally a second pool ({@link Bonus}, e.g. "Star 1-12, pick 2"): every line also carries bonus numbers.
 * LOTTO is 6 from 1-49 with 6 drawn; KENO is 1-12 spots from 1-80 with 20 drawn.
 */
public record PickGame(String title, int min, int max, int pick, int draw, Bonus bonus) {

    public static final int MAX_NUMBERS = 100;
    public static final int MAX_PICK = 20;

    /** The second pool: {@code pick} numbers from {@code min}..{@code max}, {@code draw} of them drawn. */
    public record Bonus(String label, int min, int max, int pick, int draw) {
        public Bonus {
            label = label == null || label.isBlank() ? "Bonus" : label.trim();
            Checks.that(label.length() <= 30, "The bonus name is at most 30 characters.");
            Checks.that(min >= 0 && max >= min && max - min + 1 <= MAX_NUMBERS, label + ": numbers from-to, at most " + MAX_NUMBERS + " numbers.");
            Checks.that(pick >= 1 && pick <= Math.min(MAX_PICK, max - min + 1), label + ": pick 1 to " + Math.min(MAX_PICK, max - min + 1) + ".");
            Checks.that(draw >= 1 && draw <= max - min + 1, label + ": draw 1 to " + (max - min + 1) + ".");
        }

        public int size() {
            return max - min + 1;
        }
    }

    public PickGame {
        title = title == null || title.isBlank() ? "Game" : title.trim();
        Checks.that(title.length() <= 60, "The game name is at most 60 characters.");
        Checks.that(min >= 0 && max > min && max - min + 1 <= MAX_NUMBERS, "The numbers go from-to, 2 to " + MAX_NUMBERS + " numbers.");
        int size = max - min + 1;
        Checks.that(pick >= 1 && pick <= Math.min(MAX_PICK, size), "A line has 1 to " + Math.min(MAX_PICK, size) + " numbers.");
        Checks.that(draw >= 1 && draw <= size, "1 to " + size + " numbers are drawn.");
    }

    /** Numbers 1..numbers, no bonus (LOTTO, KENO). */
    public PickGame(int numbers, int pick, int draw) {
        this(null, 1, numbers, pick, draw, null);
    }

    public int size() {
        return max - min + 1;
    }

    public boolean hasBonus() {
        return bonus != null;
    }

    public int bonusPick() {
        return bonus == null ? 0 : bonus.pick();
    }

    public void number(int n, String where) {
        Checks.that(n >= min && n <= max, where + ": " + n + " is not a number of the game (" + min + " to " + max + ").");
    }

    public void bonusNumber(int n, String where) {
        Checks.that(bonus != null, where + ": this game has no " + "bonus numbers.");
        Checks.that(n >= bonus.min() && n <= bonus.max(), where + ": " + n + " is not a " + bonus.label() + " number (" + bonus.min() + " to " + bonus.max() + ").");
    }
}
