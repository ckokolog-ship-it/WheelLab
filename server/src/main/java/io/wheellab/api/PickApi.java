package io.wheellab.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.wheellab.core.Checks;
import io.wheellab.core.Combinatorics;
import io.wheellab.core.Histograms;
import io.wheellab.core.LineSource;
import io.wheellab.pick.Entry;
import io.wheellab.pick.Pick;
import io.wheellab.pick.PickGame;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** JSON handlers of the number-game endpoints (see {@link Pick} for the request format). */
final class PickApi {

    private final LineStore store;

    PickApi(LineStore store) {
        this.store = store;
    }

    /** {total, entries:[{type, count}]} */
    JsonObject count(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        JsonObject out = new JsonObject();
        out.addProperty("total", req.count());
        JsonArray entries = new JsonArray();
        for (Entry e : req.entries()) {
            JsonObject o = new JsonObject();
            o.addProperty("type", e.type());
            o.addProperty("count", e.count());
            entries.add(o);
        }
        out.add("entries", entries);
        return out;
    }

    /** {id, total, perEntry} */
    JsonObject build(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        LineSource<String> lines = req.lines();
        JsonObject out = new JsonObject();
        out.addProperty("id", store.put(lines));
        out.addProperty("total", lines.size());
        List<Long> per = new ArrayList<>();
        for (Entry e : req.entries()) per.add(e.count());
        out.add("perEntry", Json.longs(per));
        return out;
    }

    /**
     * {total, histogram, entries:[{histogram}], payout?} -- {@code payouts[h]} (optional) is the prize of a
     * line with h hits; the payout is the sum over all lines.
     */
    JsonObject check(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        Set<Integer> drawn = Pick.drawn(body, req.game());
        int k = req.game().pick();
        long[] grand = new long[k + 1];
        JsonArray entries = new JsonArray();
        for (Entry e : req.entries()) {
            long[] h = e.histogram(drawn);
            grand = Histograms.add(grand, h);
            JsonObject o = new JsonObject();
            o.addProperty("type", e.type());
            o.add("histogram", Json.longs(h));
            entries.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("total", req.count());
        out.add("histogram", Json.longs(grand));
        out.add("entries", entries);
        if (body.has("payouts") && body.get("payouts").isJsonArray()) {
            JsonArray p = body.getAsJsonArray("payouts");
            Checks.that(p.size() <= k + 1, "Prizes: one per number of hits, 0 to " + k + ".");
            BigDecimal total = BigDecimal.ZERO;
            for (int h = 0; h < p.size(); h++) {
                JsonElement v = p.get(h);
                if (v.isJsonNull()) continue;
                BigDecimal prize = v.getAsBigDecimal();
                Checks.that(prize.signum() >= 0, "Prizes cannot be negative.");
                total = total.add(prize.multiply(BigDecimal.valueOf(grand[h])));
            }
            out.addProperty("payout", total.stripTrailingZeros().toPlainString());
        }
        return out;
    }

    /** {id, total}: the lines with at least (or exactly) {@code minHits} hits. */
    JsonObject checkLines(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        Set<Integer> drawn = Pick.drawn(body, req.game());
        int k = req.game().pick();
        int minHits = body.has("minHits") ? body.get("minHits").getAsInt() : k;
        boolean exact = body.has("exact") && body.get("exact").getAsBoolean();
        Checks.that(minHits >= 0 && minHits <= k, "Hits are 0 to " + k + ".");
        LineSource<String> lines = req.lines();
        Checks.that(lines.size() <= MatchXApi.MAX_SCAN, String.format("Show lines checks every line -- up to %,d (these entries have %,d). "
                + "The hit table works for any size.", MatchXApi.MAX_SCAN, lines.size()));
        List<List<String>> found = new ArrayList<>();
        for (long i = 0; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            int hits = 0;
            for (String s : line) if (drawn.contains(Integer.parseInt(s))) hits++;
            if (exact ? hits == minHits : hits >= minHits) {
                Checks.that(found.size() < MatchXApi.MAX_FOUND, String.format("More than %,d lines match -- raise the number of hits.", MatchXApi.MAX_FOUND));
                found.add(line);
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("id", store.put(LineSource.of(found)));
        out.addProperty("total", found.size());
        return out;
    }

    /**
     * {total, rows:[{line, rank}|{line, error}]}: where each line is among all lines of the game (or of a
     * system on {@code pool}), in lexicographic order -- rank 1 is the first line.
     */
    JsonObject position(JsonObject body) {
        PickGame game = Pick.game(body);
        List<Integer> pool = new ArrayList<>();
        if (body.has("pool") && body.get("pool").isJsonArray() && !body.getAsJsonArray("pool").isEmpty()) {
            TreeSet<Integer> set = new TreeSet<>();
            for (JsonElement e : body.getAsJsonArray("pool")) {
                game.number(e.getAsInt(), "Pool");
                Checks.that(set.add(e.getAsInt()), "Pool: " + e.getAsInt() + " is given twice.");
            }
            pool.addAll(set);
            Checks.that(pool.size() >= game.pick(), "Pool: at least " + game.pick() + " numbers.");
        } else {
            for (int n = 1; n <= game.numbers(); n++) pool.add(n);
        }
        Checks.that(body.has("lines") && body.get("lines").isJsonArray(), "Give the lines to find.");
        JsonArray lines = body.getAsJsonArray("lines");
        Checks.that(lines.size() <= 1000, "At most 1,000 lines at a time.");
        JsonObject out = new JsonObject();
        out.addProperty("total", Combinatorics.binomial(pool.size(), game.pick()));
        JsonArray rows = new JsonArray();
        for (JsonElement e : lines) {
            String text = e.getAsString();
            JsonObject row = new JsonObject();
            row.addProperty("line", text);
            try {
                TreeSet<Integer> nums = new TreeSet<>();
                for (String p : text.trim().split("[,\\s]+"))
                    if (!p.isBlank()) Checks.that(nums.add(Integer.parseInt(p)), "a number is given twice.");
                Checks.that(nums.size() == game.pick(), "a line has " + game.pick() + " numbers.");
                int[] pos = new int[nums.size()];
                int i = 0;
                for (int n : nums) {
                    int at = pool.indexOf(n);
                    Checks.that(at >= 0, n + " is not in the pool.");
                    pos[i++] = at;
                }
                row.addProperty("rank", Combinatorics.rank(pool.size(), pos) + 1);
            } catch (NumberFormatException ex) {
                row.addProperty("error", "only numbers, separated by spaces or commas.");
            } catch (IllegalArgumentException ex) {
                row.addProperty("error", ex.getMessage());
            }
            rows.add(row);
        }
        out.add("rows", rows);
        return out;
    }
}
