package io.wheellab.pick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.wheellab.core.Checks;
import io.wheellab.core.LineSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Number-game requests (LOTTO, KENO, ...). The entries' lines are appended one after another.
 * <pre>
 * {"game": {"numbers": 49, "pick": 6, "draw": 6},
 *  "entries": [
 *    {"type": "single", "numbers": [3, 11, 17, 25, 38, 44]},
 *    {"type": "system", "numbers": [1, 2, 3, 4, 5, 6, 7, 8]},
 *    {"type": "groups", "groups": [[1, 2, 3, 4], [20, 21, 22, 23, 24]], "take": [2, 4]},
 *    {"type": "wheel",  "numbers": [1, 5, 9, 13, 17, 21, 25, 29, 33, 37], "guarantee": 3}]}
 * </pre>
 */
public final class Pick {

    public static final int MAX_ENTRIES = 100;

    private Pick() {
    }

    public record Request(PickGame game, List<Entry> entries) {
        public long count() {
            long total = 0;
            for (Entry e : entries) total = Math.addExact(total, e.count());
            return total;
        }

        public LineSource<String> lines() {
            return LineSource.concat(entries.stream().map(Entry::lines).toList());
        }
    }

    public static PickGame game(JsonObject root) {
        Checks.that(root.has("game") && root.get("game").isJsonObject(), "Missing the game settings.");
        JsonObject g = root.getAsJsonObject("game");
        return new PickGame(integer(g, "numbers"), integer(g, "pick"), integer(g, "draw"));
    }

    public static Request parse(JsonObject root) {
        PickGame game = game(root);
        Checks.that(root.has("entries") && root.get("entries").isJsonArray() && !root.getAsJsonArray("entries").isEmpty(), "Add at least one entry.");
        JsonArray arr = root.getAsJsonArray("entries");
        Checks.that(arr.size() <= MAX_ENTRIES, "At most " + MAX_ENTRIES + " entries.");
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            try {
                entries.add(entry(game, arr.get(i).getAsJsonObject()));
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalArgumentException("Entry " + (i + 1) + ": " + e.getMessage(), e);
            }
        }
        return new Request(game, entries);
    }

    static Entry entry(PickGame game, JsonObject o) {
        String type = o.has("type") ? o.get("type").getAsString() : "";
        return switch (type) {
            case "single" -> new Entries.Single(game, ints(o, "numbers"));
            case "system" -> new Entries.Pool(game, ints(o, "numbers"));
            case "groups" -> {
                Checks.that(o.has("groups") && o.get("groups").isJsonArray(), "Groups: add the groups.");
                List<List<Integer>> groups = new ArrayList<>();
                for (JsonElement g : o.getAsJsonArray("groups")) groups.add(ints(g.getAsJsonArray()));
                yield new Entries.Groups(game, groups, ints(o, "take"));
            }
            case "wheel" -> new Entries.Wheel(game, ints(o, "numbers"), integer(o, "guarantee"));
            default -> throw new IllegalArgumentException("Unknown entry type \"" + type + "\" (single, system, groups or wheel).");
        };
    }

    /** The drawn numbers: exactly {@code draw} distinct numbers of the game. */
    public static Set<Integer> drawn(JsonObject root, PickGame game) {
        List<Integer> raw = ints(root, "drawn");
        Set<Integer> set = new HashSet<>();
        for (int n : raw) {
            game.number(n, "Drawn numbers");
            Checks.that(set.add(n), "Drawn numbers: " + n + " is given twice.");
        }
        Checks.that(set.size() == game.draw(), "Give the " + game.draw() + " drawn numbers (now " + set.size() + ").");
        return set;
    }

    static List<Integer> ints(JsonObject o, String key) {
        Checks.that(o.has(key) && o.get(key).isJsonArray(), "Missing \"" + key + "\".");
        return ints(o.getAsJsonArray(key));
    }

    static List<Integer> ints(JsonArray a) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement e : a) {
            Checks.that(e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() && e.getAsDouble() == Math.rint(e.getAsDouble()), "Expected whole numbers.");
            out.add(e.getAsInt());
        }
        return out;
    }

    static int integer(JsonObject o, String key) {
        Checks.that(o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber(), "Missing \"" + key + "\".");
        double v = o.get(key).getAsDouble();
        Checks.that(v == Math.rint(v), "\"" + key + "\" must be a whole number.");
        return (int) v;
    }
}
