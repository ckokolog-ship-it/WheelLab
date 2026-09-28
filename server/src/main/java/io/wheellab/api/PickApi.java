package io.wheellab.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.wheellab.core.Checks;
import io.wheellab.core.Combinatorics;
import io.wheellab.core.LineSource;
import io.wheellab.pick.Entry;
import io.wheellab.pick.Parts;
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
     * {total, histogram, grid?, entries:[{type, histogram, grid?}], payout?}. {@code histogram[h]} counts main
     * hits; with a bonus pool {@code grid[h][s]} splits them by bonus hits. Prizes (optional) are per line:
     * {@code payouts[h]} -- or {@code payouts[h][s]} in games with a bonus pool.
     */
    JsonObject check(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        PickGame game = req.game();
        Set<Integer> drawn = Pick.drawn(body, game);
        Set<Integer> drawnBonus = Pick.drawnBonus(body, game);
        int k = game.pick(), bp = game.bonusPick();
        long[][] grand = new long[k + 1][bp + 1];
        JsonArray entries = new JsonArray();
        for (Entry e : req.entries()) {
            long[][] g = e.histogram(drawn, drawnBonus);
            for (int h = 0; h <= k; h++) for (int s = 0; s <= bp; s++) grand[h][s] = Math.addExact(grand[h][s], g[h][s]);
            JsonObject o = new JsonObject();
            o.addProperty("type", e.type());
            o.add("histogram", Json.longs(rowSums(g)));
            if (game.hasBonus()) o.add("grid", grid(g));
            entries.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("total", req.count());
        out.add("histogram", Json.longs(rowSums(grand)));
        if (game.hasBonus()) out.add("grid", grid(grand));
        out.add("entries", entries);
        if (body.has("payouts") && body.get("payouts").isJsonArray()) out.addProperty("payout", payout(body.getAsJsonArray("payouts"), grand, game).toPlainString());
        return out;
    }

    private static BigDecimal payout(JsonArray p, long[][] grand, PickGame game) {
        int k = game.pick(), bp = game.bonusPick();
        Checks.that(p.size() <= k + 1, "Prizes: one per number of hits, 0 to " + k + ".");
        BigDecimal total = BigDecimal.ZERO;
        for (int h = 0; h < p.size(); h++) {
            JsonElement v = p.get(h);
            if (v.isJsonNull()) continue;
            if (v.isJsonArray()) {
                Checks.that(game.hasBonus() && v.getAsJsonArray().size() <= bp + 1, "Prizes: per main hits and bonus hits (0 to " + bp + ").");
                for (int s = 0; s < v.getAsJsonArray().size(); s++) {
                    JsonElement c = v.getAsJsonArray().get(s);
                    if (!c.isJsonNull()) total = total.add(prize(c).multiply(BigDecimal.valueOf(grand[h][s])));
                }
            } else {
                long lines = 0;
                for (long x : grand[h]) lines = Math.addExact(lines, x);
                total = total.add(prize(v).multiply(BigDecimal.valueOf(lines)));
            }
        }
        return total.stripTrailingZeros();
    }

    private static BigDecimal prize(JsonElement v) {
        BigDecimal p = v.getAsBigDecimal();
        Checks.that(p.signum() >= 0, "Prizes cannot be negative.");
        return p;
    }

    private static long[] rowSums(long[][] g) {
        long[] out = new long[g.length];
        for (int h = 0; h < g.length; h++) for (long x : g[h]) out[h] = Math.addExact(out[h], x);
        return out;
    }

    private static JsonArray grid(long[][] g) {
        JsonArray a = new JsonArray();
        for (long[] row : g) a.add(Json.longs(row));
        return a;
    }

    /** {id, total}: lines with at least (or exactly) {@code minHits} main hits, and at least {@code minBonus} bonus hits. */
    JsonObject checkLines(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        PickGame game = req.game();
        Set<Integer> drawn = Pick.drawn(body, game);
        Set<Integer> drawnBonus = Pick.drawnBonus(body, game);
        int k = game.pick();
        int minHits = body.has("minHits") ? body.get("minHits").getAsInt() : k;
        int minBonus = body.has("minBonus") ? body.get("minBonus").getAsInt() : 0;
        boolean exact = body.has("exact") && body.get("exact").getAsBoolean();
        Checks.that(minHits >= 0 && minHits <= k, "Hits are 0 to " + k + ".");
        LineSource<String> lines = req.lines();
        Checks.that(lines.size() <= MatchXApi.MAX_SCAN, String.format("Show lines checks every line -- up to %,d (these entries have %,d). "
                + "The hit table works for any size.", MatchXApi.MAX_SCAN, lines.size()));
        List<List<String>> found = new ArrayList<>();
        for (long i = 0; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            int hits = 0, bonusHits = 0;
            for (String s : line) {
                if (s.startsWith("+")) {
                    if (drawnBonus.contains(Integer.parseInt(s.substring(1)))) bonusHits++;
                } else if (drawn.contains(Integer.parseInt(s))) {
                    hits++;
                }
            }
            if ((exact ? hits == minHits : hits >= minHits) && bonusHits >= minBonus) {
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
     * system on {@code pool} / {@code bonusPool}), in lexicographic order -- rank 1 is the first line. Bonus
     * numbers follow a "|" or are written +n; with a bonus pool, the main numbers change slowest.
     */
    JsonObject position(JsonObject body) {
        PickGame game = Pick.game(body);
        List<Integer> pool = pool(body, "pool", game.min(), game.max(), game.pick(), "Pool");
        List<Integer> bonusPool = game.hasBonus()
                ? pool(body, "bonusPool", game.bonus().min(), game.bonus().max(), game.bonusPick(), game.bonus().label() + " pool")
                : List.of();
        long bonusTotal = game.hasBonus() ? Combinatorics.binomial(bonusPool.size(), game.bonusPick()) : 1;
        Checks.that(body.has("lines") && body.get("lines").isJsonArray(), "Give the lines to find.");
        JsonArray lines = body.getAsJsonArray("lines");
        Checks.that(lines.size() <= 1000, "At most 1,000 lines at a time.");
        JsonObject out = new JsonObject();
        out.addProperty("total", Math.multiplyExact(Combinatorics.binomial(pool.size(), game.pick()), bonusTotal));
        JsonArray rows = new JsonArray();
        for (JsonElement e : lines) {
            String text = e.getAsString();
            JsonObject row = new JsonObject();
            row.addProperty("line", text);
            try {
                TreeSet<Integer> main = new TreeSet<>(), bonus = new TreeSet<>();
                boolean afterBar = false;
                for (String p : text.trim().replace("|", " | ").split("[,\\s]+")) {
                    if (p.isBlank()) continue;
                    if (p.equals("|")) {
                        afterBar = true;
                        continue;
                    }
                    boolean isBonus = afterBar || p.startsWith("+");
                    int n = Integer.parseInt(p.startsWith("+") ? p.substring(1) : p);
                    Checks.that((isBonus ? bonus : main).add(n), "a number is given twice.");
                }
                Checks.that(main.size() == game.pick(), "a line has " + game.pick() + " numbers.");
                Checks.that(bonus.size() == game.bonusPick(), game.hasBonus() ? "give " + game.bonusPick() + " " + game.bonus().label() + " after a |." : "this game has no bonus numbers.");
                long rank = Math.multiplyExact(rankIn(pool, main, "the pool"), bonusTotal);
                if (game.hasBonus()) rank = Math.addExact(rank, rankIn(bonusPool, bonus, "the " + game.bonus().label() + " pool"));
                row.addProperty("rank", rank + 1);
            } catch (NumberFormatException ex) {
                row.addProperty("error", "only numbers, separated by spaces or commas (bonus after a |).");
            } catch (IllegalArgumentException ex) {
                row.addProperty("error", ex.getMessage());
            }
            rows.add(row);
        }
        out.add("rows", rows);
        return out;
    }

    private static List<Integer> pool(JsonObject body, String key, int min, int max, int pick, String what) {
        List<Integer> pool = new ArrayList<>();
        if (body.has(key) && body.get(key).isJsonArray() && !body.getAsJsonArray(key).isEmpty()) {
            TreeSet<Integer> set = new TreeSet<>();
            for (JsonElement e : body.getAsJsonArray(key)) {
                int n = e.getAsInt();
                Checks.that(n >= min && n <= max, what + ": " + n + " is outside " + min + " to " + max + ".");
                Checks.that(set.add(n), what + ": " + n + " is given twice.");
            }
            pool.addAll(set);
            Checks.that(pool.size() >= pick, what + ": at least " + pick + " numbers.");
        } else {
            for (int n = min; n <= max; n++) pool.add(n);
        }
        return pool;
    }

    private static long rankIn(List<Integer> pool, Set<Integer> numbers, String where) {
        int[] pos = new int[numbers.size()];
        int i = 0;
        for (int n : numbers) {
            int at = pool.indexOf(n);
            Checks.that(at >= 0, n + " is not in " + where + ".");
            pos[i++] = at;
        }
        return Combinatorics.rank(pool.size(), pos);
    }

    /**
     * {k, poolSize, bonusCombos, total, oddEven:{byOdd, moreOdd, moreEven, equal}, lastDigit:[{digit, size, byCount}]}
     * for the system entry {@code entry}: how many of its lines have j odd numbers, and j numbers ending in
     * each digit -- C(m, j) x C(v - m, k - j), times the bonus combinations. No line is built.
     */
    JsonObject analysis(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        int idx = body.has("entry") ? body.get("entry").getAsInt() : -1;
        Checks.that(idx >= 0 && idx < req.entries().size() && req.entries().get(idx).main() instanceof Parts.Pool,
                "The analysis is for a System entry.");
        Entry e = req.entries().get(idx);
        List<Integer> pool = ((Parts.Pool) e.main()).pool();
        int k = req.game().pick();
        long mult = e.bonusCount();
        int odd = (int) pool.stream().filter(n -> n % 2 != 0).count();
        long[] byOdd = split(odd, pool.size() - odd, k, mult);
        long moreOdd = 0, moreEven = 0, equal = 0;
        for (int j = 0; j <= k; j++) {
            if (j > k - j) moreOdd += byOdd[j];
            else if (j < k - j) moreEven += byOdd[j];
            else equal += byOdd[j];
        }
        JsonObject oe = new JsonObject();
        oe.add("byOdd", Json.longs(byOdd));
        oe.addProperty("moreOdd", moreOdd);
        oe.addProperty("moreEven", moreEven);
        oe.addProperty("equal", equal);
        JsonArray digits = new JsonArray();
        for (int d = 0; d < 10; d++) {
            int digit = d;
            int m = (int) pool.stream().filter(n -> n % 10 == digit).count();
            JsonObject o = new JsonObject();
            o.addProperty("digit", d);
            o.addProperty("size", m);
            o.add("byCount", Json.longs(split(m, pool.size() - m, k, mult)));
            digits.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("k", k);
        out.addProperty("poolSize", pool.size());
        out.addProperty("bonusCombos", mult);
        out.addProperty("total", e.count());
        out.add("oddEven", oe);
        out.add("lastDigit", digits);
        return out;
    }

    /** by[j] = C(in, j) x C(out, k - j) x mult. */
    private static long[] split(int in, int out, int k, long mult) {
        long[] by = new long[k + 1];
        for (int j = 0; j <= k; j++)
            by[j] = Math.multiplyExact(Math.multiplyExact(Combinatorics.binomial(in, j), Combinatorics.binomial(out, k - j)), mult);
        return by;
    }

    /** {total, main:[{number, count}], bonus:[{number, count}]}: in how many lines of the slip each number is. */
    JsonObject frequency(JsonObject body) {
        Pick.Request req = Pick.parse(body);
        PickGame game = req.game();
        long[] main = new long[game.size()];
        long[] bonus = new long[game.hasBonus() ? game.bonus().size() : 0];
        for (Entry e : req.entries()) {
            long[] f = e.frequency(), b = e.bonusFrequency();
            for (int i = 0; i < main.length; i++) main[i] = Math.addExact(main[i], f[i]);
            for (int i = 0; i < bonus.length; i++) bonus[i] = Math.addExact(bonus[i], b[i]);
        }
        JsonObject out = new JsonObject();
        out.addProperty("total", req.count());
        out.add("main", counts(main, game.min()));
        out.add("bonus", counts(bonus, game.hasBonus() ? game.bonus().min() : 0));
        return out;
    }

    private static JsonArray counts(long[] f, int min) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < f.length; i++) {
            if (f[i] == 0) continue;
            JsonObject o = new JsonObject();
            o.addProperty("number", min + i);
            o.addProperty("count", f[i]);
            a.add(o);
        }
        return a;
    }

    /** {text} -> {game, entries, prizes} (see {@link io.wheellab.pick.GameText}). */
    JsonObject parse(JsonObject body) {
        Checks.that(body.has("text") && body.get("text").isJsonPrimitive(), "Give the game text.");
        return io.wheellab.pick.GameText.parse(body.get("text").getAsString());
    }

    /** {game, entries, prizes?} -> {text} */
    JsonObject text(JsonObject body) {
        JsonObject out = new JsonObject();
        out.addProperty("text", io.wheellab.pick.GameText.write(body));
        return out;
    }
}
