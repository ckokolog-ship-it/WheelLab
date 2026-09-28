package io.wheellab.matchx;

import io.wheellab.core.Checks;

import java.util.HashSet;
import java.util.List;

/**
 * The outcomes a MatchX match can be played on. Every match uses the symbols of exactly one family:
 * <ul>
 *   <li>{@code 1 X 2} -- home win, draw, away win</li>
 *   <li>{@code U O} -- under / over a goal line</li>
 *   <li>{@code GG NG} -- both teams score / not both</li>
 * </ul>
 */
public final class Symbols {

    public static final List<List<String>> FAMILIES = List.of(
            List.of("1", "X", "2"),
            List.of("U", "O"),
            List.of("GG", "NG"));

    /** Shown in a line for a match that no group plays. */
    public static final String NONE = "-";

    private Symbols() {
    }

    /** The family of {@code symbol}, or null when it is unknown. */
    public static List<String> familyOf(String symbol) {
        for (List<String> f : FAMILIES) if (f.contains(symbol)) return f;
        return null;
    }

    /** Upper case; a Greek capital chi (U+03A7, looks like X) is read as X. */
    public static String normalize(String symbol) {
        return symbol == null ? "" : symbol.trim().toUpperCase().replace('\u03A7', 'X');
    }

    /**
     * Validates the symbols played on one match: at least one, no repeats, all of one family. The order is
     * kept -- it matters for the Errors (first = prediction), Ranges (preference order) and Covering groups.
     */
    public static List<String> pick(List<String> raw, int match) {
        Checks.that(raw != null && !raw.isEmpty(), "Match " + match + ": choose at least one outcome.");
        List<String> out = raw.stream().map(Symbols::normalize).toList();
        List<String> family = familyOf(out.get(0));
        Checks.that(family != null, "Match " + match + ": unknown outcome \"" + out.get(0) + "\" (use 1, X, 2, U, O, GG or NG).");
        for (String s : out)
            Checks.that(family.contains(s), "Match " + match + ": \"" + s + "\" and \"" + out.get(0) + "\" are outcomes of different kinds -- a match is played on 1X2, Under/Over or GG/NG.");
        Checks.that(new HashSet<>(out).size() == out.size(), "Match " + match + ": an outcome is chosen twice.");
        return out;
    }
}
