package io.wheellab.matchx;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.wheellab.core.Checks;
import io.wheellab.core.LineSource;

import java.util.ArrayList;
import java.util.List;

/**
 * MatchX requests. A request holds the number of matches and one or more tickets; the tickets' lines are
 * appended one after another.
 * <pre>
 * {"matches": 13,
 *  "tickets": [{"groups": [
 *     {"type": "full",     "matches": [1,2,3], "picks": [["1","X"],["2"],["1","X","2"]]},
 *     {"type": "errors",   "matches": [4,5,6], "picks": [...], "errors": [0,1]},
 *     {"type": "ranges",   "matches": [7,8,9], "picks": [...], "ranges": [[1,3],[0,2],[0,1]]},
 *     {"type": "covering", "matches": [10,11,12,13], "picks": [...], "radius": 2, "guarantee": 3}]}]}
 * </pre>
 * In errors and covering groups the first pick of a match is the prediction; in ranges groups the picks
 * are in order of preference.
 */
public final class MatchX {

    public static final int MAX_TICKETS = 10;

    private MatchX() {
    }

    public record Request(int matches, List<Ticket> tickets) {
        public long count() {
            long total = 0;
            for (Ticket t : tickets) total = Math.addExact(total, t.count());
            return total;
        }

        public LineSource<String> lines() {
            return LineSource.concat(tickets.stream().map(Ticket::lines).toList());
        }
    }

    public static Request parse(JsonObject root) {
        int matches = intField(root, "matches", "the number of matches");
        JsonArray tickets = array(root, "tickets", "Add at least one ticket.");
        Checks.that(!tickets.isEmpty() && tickets.size() <= MAX_TICKETS, "1 to " + MAX_TICKETS + " tickets.");
        List<Ticket> out = new ArrayList<>();
        for (int t = 0; t < tickets.size(); t++) {
            try {
                JsonArray groups = array(tickets.get(t).getAsJsonObject(), "groups", "A ticket needs at least one group.");
                List<Group> gs = new ArrayList<>();
                for (int g = 0; g < groups.size(); g++) {
                    try {
                        gs.add(group(groups.get(g).getAsJsonObject()));
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        throw new IllegalArgumentException("Group " + (g + 1) + ": " + e.getMessage(), e);
                    }
                }
                out.add(new Ticket(matches, gs));
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IllegalArgumentException(tickets.size() > 1 ? "Ticket " + (t + 1) + ": " + e.getMessage() : e.getMessage(), e);
            }
        }
        return new Request(matches, out);
    }

    static Group group(JsonObject o) {
        String type = o.has("type") ? o.get("type").getAsString() : "full";
        List<Integer> ms = ints(array(o, "matches", "A group needs at least one match."));
        for (int i = 1; i < ms.size(); i++) Checks.that(ms.get(i) > ms.get(i - 1), "The matches of a group must be listed once, in increasing order.");
        JsonArray picksJson = array(o, "picks", "Choose the outcomes of every match.");
        Checks.that(picksJson.size() == ms.size(), "Each match of the group needs its outcomes.");
        List<List<String>> picks = new ArrayList<>();
        for (int i = 0; i < picksJson.size(); i++) {
            List<String> raw = new ArrayList<>();
            for (JsonElement e : picksJson.get(i).getAsJsonArray()) raw.add(e.getAsString());
            picks.add(Symbols.pick(raw, ms.get(i)));
        }
        return switch (type) {
            case "full" -> new FullGroup(ms, picks);
            case "errors" -> new ErrorsGroup(ms, picks, ints(array(o, "errors", "Choose how many errors to play.")));
            case "ranges" -> {
                JsonArray r = array(o, "ranges", "Give the ranges of the 1st, 2nd and 3rd choice.");
                int[][] ranges = new int[r.size()][];
                for (int i = 0; i < r.size(); i++) ranges[i] = ints(r.get(i).getAsJsonArray()).stream().mapToInt(Integer::intValue).toArray();
                yield new RangesGroup(ms, picks, ranges);
            }
            case "covering" -> new CoveringGroup(ms, picks, intField(o, "radius", "the changes to cover"), intField(o, "guarantee", "the guaranteed hits"));
            default -> throw new IllegalArgumentException("Unknown group type \"" + type + "\" (full, errors, ranges or covering).");
        };
    }

    /** The result of every match, e.g. ["1","X","U","GG",...]. */
    public static List<String> result(JsonObject root, int matches) {
        JsonArray r = array(root, "result", "Give the result of every match.");
        Checks.that(r.size() == matches, "The result needs one outcome per match (" + matches + ").");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < r.size(); i++) {
            String s = Symbols.normalize(r.get(i).getAsString());
            Checks.that(Symbols.familyOf(s) != null, "Result of match " + (i + 1) + ": unknown outcome \"" + s + "\".");
            out.add(s);
        }
        return out;
    }

    static JsonArray array(JsonObject o, String key, String missing) {
        Checks.that(o != null && o.has(key) && o.get(key).isJsonArray(), missing);
        return o.getAsJsonArray(key);
    }

    static int intField(JsonObject o, String key, String what) {
        Checks.that(o != null && o.has(key) && o.get(key).isJsonPrimitive() && o.get(key).getAsJsonPrimitive().isNumber(), "Missing " + what + ".");
        double v = o.get(key).getAsDouble();
        Checks.that(v == Math.rint(v), "The " + what + " must be a whole number.");
        return (int) v;
    }

    static List<Integer> ints(JsonArray a) {
        List<Integer> out = new ArrayList<>();
        for (JsonElement e : a) {
            Checks.that(e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber(), "Expected numbers.");
            out.add(e.getAsInt());
        }
        return out;
    }
}
