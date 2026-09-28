package io.wheellab.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.wheellab.core.Checks;
import io.wheellab.core.LineSource;
import io.wheellab.matchx.Group;
import io.wheellab.core.Histograms;
import io.wheellab.matchx.MatchX;
import io.wheellab.matchx.Ticket;

import java.util.ArrayList;
import java.util.List;

/** JSON handlers of the MatchX endpoints (see {@link MatchX} for the request format). */
final class MatchXApi {

    /** "Show lines" checks lines one by one: at most this many lines are scanned... */
    static final long MAX_SCAN = 20_000_000L;
    /** ...and at most this many are returned. */
    static final int MAX_FOUND = 200_000;

    private final LineStore store;

    MatchXApi(LineStore store) {
        this.store = store;
    }

    /** {total, tickets:[{total, uncovered, groups:[{type, matches, count}]}]} */
    JsonObject count(JsonObject body) {
        MatchX.Request req = MatchX.parse(body);
        JsonObject out = new JsonObject();
        out.addProperty("total", req.count());
        JsonArray tickets = new JsonArray();
        for (Ticket t : req.tickets()) {
            JsonObject to = new JsonObject();
            to.addProperty("total", t.count());
            to.add("uncovered", Json.ints(t.uncovered()));
            JsonArray groups = new JsonArray();
            for (Group g : t.groups()) {
                JsonObject go = new JsonObject();
                go.addProperty("type", g.type());
                go.add("matches", Json.ints(g.matches()));
                go.addProperty("count", g.count());
                groups.add(go);
            }
            to.add("groups", groups);
            tickets.add(to);
        }
        out.add("tickets", tickets);
        return out;
    }

    /** {id, total, perTicket:[..]} -- read the lines with GET /api/lines. */
    JsonObject build(JsonObject body) {
        MatchX.Request req = MatchX.parse(body);
        LineSource<String> lines = req.lines();
        JsonObject out = new JsonObject();
        out.addProperty("id", store.put(lines));
        out.addProperty("total", lines.size());
        List<Long> per = new ArrayList<>();
        for (Ticket t : req.tickets()) per.add(t.count());
        out.add("perTicket", Json.longs(per));
        return out;
    }

    /** {total, histogram, tickets:[{histogram, groups:[{type, matches, histogram}]}]} -- no line is built. */
    JsonObject check(JsonObject body) {
        MatchX.Request req = MatchX.parse(body);
        List<String> result = MatchX.result(body, req.matches());
        long[] grand = new long[req.matches() + 1];
        JsonArray tickets = new JsonArray();
        for (Ticket t : req.tickets()) {
            JsonObject to = new JsonObject();
            JsonArray groups = new JsonArray();
            for (Group g : t.groups()) {
                JsonObject go = new JsonObject();
                go.addProperty("type", g.type());
                go.add("matches", Json.ints(g.matches()));
                go.add("histogram", Json.longs(t.groupHistogram(g, result)));
                groups.add(go);
            }
            long[] hist = t.histogram(result);
            grand = Histograms.add(grand, hist);
            to.add("histogram", Json.longs(hist));
            to.add("groups", groups);
            tickets.add(to);
        }
        JsonObject out = new JsonObject();
        out.addProperty("total", req.count());
        out.add("histogram", Json.longs(grand));
        out.add("tickets", tickets);
        return out;
    }

    /** {id, total}: the lines with at least (or exactly) {@code minHits} hits, in ticket order. */
    JsonObject checkLines(JsonObject body) {
        MatchX.Request req = MatchX.parse(body);
        List<String> result = MatchX.result(body, req.matches());
        int minHits = body.has("minHits") ? body.get("minHits").getAsInt() : Math.max(0, req.matches() - 2);
        boolean exact = body.has("exact") && body.get("exact").getAsBoolean();
        Checks.that(minHits >= 0 && minHits <= req.matches(), "Hits are 0 to " + req.matches() + ".");
        LineSource<String> lines = req.lines();
        Checks.that(lines.size() <= MAX_SCAN, String.format("Show lines checks every line -- up to %,d (this system has %,d). "
                + "The hit table above works for any size.", MAX_SCAN, lines.size()));
        List<List<String>> found = new ArrayList<>();
        for (long i = 0; i < lines.size(); i++) {
            List<String> line = lines.get(i);
            int hits = 0;
            for (int m = 0; m < line.size(); m++) if (line.get(m).equals(result.get(m))) hits++;
            if (exact ? hits == minHits : hits >= minHits) {
                Checks.that(found.size() < MAX_FOUND, String.format("More than %,d lines match -- raise the number of hits.", MAX_FOUND));
                found.add(line);
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("id", store.put(LineSource.of(found)));
        out.addProperty("total", found.size());
        return out;
    }
}
