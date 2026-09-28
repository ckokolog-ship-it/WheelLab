package io.wheellab.pick;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The game language: a whole number game -- settings, prizes and entries -- as plain text.
 * <pre>
 * # My game (lines starting with # are comments)
 * game "Star 5" numbers 1-50 pick 5 draw 5
 * bonus "Star" numbers 1-12 pick 2 draw 2
 * prize 5+2 = 1000000
 * prize 5 = 1000            (5 main hits, any bonus hits)
 * entry single 3 11 17 25 38 | 4 9
 * entry system 1 2 3 4 5 6 7 | 1 2 3
 * entry groups 1 2 3 4 take 2 ; 20 21 22 23 24 take 3 | 5 6
 * entry wheel 1 5 9 13 17 21 25 29 33 37 guarantee 3 | 1 2
 * </pre>
 * Bonus numbers come after "|". {@link #parse} gives {game, entries, prizes} in the API format (prizes keyed
 * "h" or "h+s"); {@link #write} turns them back into text.
 */
public final class GameText {

    private static final Pattern GAME = Pattern.compile("^game\\s+\"([^\"]{0,60})\"\\s+numbers\\s+(\\d+)-(\\d+)\\s+pick\\s+(\\d+)(?:\\s+draw\\s+(\\d+))?$");
    private static final Pattern BONUS = Pattern.compile("^bonus\\s+\"([^\"]{1,30})\"\\s+numbers\\s+(\\d+)-(\\d+)\\s+pick\\s+(\\d+)(?:\\s+draw\\s+(\\d+))?$");
    private static final Pattern PRIZE = Pattern.compile("^prize\\s+(\\d+)(?:\\+(\\d+))?\\s*=\\s*(\\d+(?:\\.\\d+)?)$");
    private static final Pattern ENTRY = Pattern.compile("^entry\\s+(single|system|groups|wheel)\\s+(.+)$");
    public static final int MAX_LINES = 1000;

    private GameText() {
    }

    /** Thrown with the line number and text of the first mistake. */
    public static final class Error extends IllegalArgumentException {
        public Error(int line, String text, String message) {
            super("Line " + line + ": " + message + "\n  " + text);
        }
    }

    public static JsonObject parse(String text) {
        String[] rows = text.replace("\r", "").split("\n", -1);
        if (rows.length > MAX_LINES) throw new IllegalArgumentException("At most " + MAX_LINES + " lines.");
        JsonObject game = null;
        JsonArray entries = new JsonArray();
        List<Integer> entryLines = new ArrayList<>();
        JsonObject prizes = new JsonObject();
        for (int i = 0; i < rows.length; i++) {
            String row = rows[i].strip();
            int n = i + 1;
            if (row.isEmpty() || row.startsWith("#")) continue;
            Matcher m;
            if ((m = GAME.matcher(row)).matches()) {
                if (game != null) throw new Error(n, row, "only one game line.");
                game = new JsonObject();
                game.addProperty("title", m.group(1));
                game.addProperty("min", Integer.parseInt(m.group(2)));
                game.addProperty("max", Integer.parseInt(m.group(3)));
                game.addProperty("pick", Integer.parseInt(m.group(4)));
                game.addProperty("draw", Integer.parseInt(m.group(5) != null ? m.group(5) : m.group(4)));
            } else if ((m = BONUS.matcher(row)).matches()) {
                if (game == null) throw new Error(n, row, "the game line comes first.");
                if (game.has("bonus")) throw new Error(n, row, "only one bonus line.");
                JsonObject b = new JsonObject();
                b.addProperty("label", m.group(1));
                b.addProperty("min", Integer.parseInt(m.group(2)));
                b.addProperty("max", Integer.parseInt(m.group(3)));
                b.addProperty("pick", Integer.parseInt(m.group(4)));
                b.addProperty("draw", Integer.parseInt(m.group(5) != null ? m.group(5) : m.group(4)));
                game.add("bonus", b);
            } else if ((m = PRIZE.matcher(row)).matches()) {
                if (game == null) throw new Error(n, row, "the game line comes first.");
                String key = m.group(1) + (m.group(2) != null ? "+" + m.group(2) : "");
                if (m.group(2) != null && !game.has("bonus")) throw new Error(n, row, "the game has no bonus numbers (add a bonus line first).");
                if (prizes.has(key)) throw new Error(n, row, "the prize for " + key + " is given twice.");
                prizes.add(key, new JsonPrimitive(new BigDecimal(m.group(3))));
            } else if ((m = ENTRY.matcher(row)).matches()) {
                if (game == null) throw new Error(n, row, "the game line comes first.");
                entries.add(entry(n, row, m.group(1), m.group(2)));
                entryLines.add(n);
            } else {
                throw new Error(n, row, "not understood. Lines start with game, bonus, prize, entry or #.");
            }
        }
        if (game == null) throw new IllegalArgumentException("Line 1: start with a game line, e.g.  game \"My game\" numbers 1-49 pick 6");
        JsonObject out = new JsonObject();
        out.add("game", game);
        out.add("entries", entries);
        out.add("prizes", prizes);
        // validate settings and entries with the engine, pointing at the first wrong entry line
        JsonObject check = new JsonObject();
        check.add("game", game);
        PickGame g;
        try {
            g = Pick.game(check);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Game settings: " + e.getMessage(), e);
        }
        for (String key : prizes.keySet()) {
            String[] hs = key.split("\\+");
            int h = Integer.parseInt(hs[0]);
            if (h > g.pick() || (hs.length > 1 && Integer.parseInt(hs[1]) > g.bonusPick()))
                throw new IllegalArgumentException("Prize " + key + ": a line has " + g.pick() + " numbers" + (g.hasBonus() ? " and " + g.bonusPick() + " " + g.bonus().label() : "") + ".");
        }
        for (int i = 0; i < entries.size(); i++) {
            try {
                Pick.entry(g, entries.get(i).getAsJsonObject());
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new Error(entryLines.get(i), rows[entryLines.get(i) - 1].strip(), e.getMessage());
            }
        }
        return out;
    }

    private static JsonObject entry(int n, String row, String type, String rest) {
        JsonObject e = new JsonObject();
        e.addProperty("type", type);
        String[] halves = rest.split("\\|", -1);
        if (halves.length > 2) throw new Error(n, row, "only one | (bonus numbers after it).");
        String main = halves[0].trim();
        try {
            switch (type) {
                case "single", "system" -> e.add("numbers", numbers(main));
                case "wheel" -> {
                    Matcher m = Pattern.compile("^(.*?)\\s+guarantee\\s+(\\d+)$").matcher(main);
                    if (!m.matches()) throw new Error(n, row, "a wheel ends with  guarantee G.");
                    e.add("numbers", numbers(m.group(1)));
                    e.addProperty("guarantee", Integer.parseInt(m.group(2)));
                }
                default -> {
                    JsonArray groups = new JsonArray(), take = new JsonArray();
                    for (String g : main.split(";")) {
                        Matcher m = Pattern.compile("^(.*?)\\s+take\\s+(\\d+)$").matcher(g.trim());
                        if (!m.matches()) throw new Error(n, row, "each group is  numbers take K, groups separated by ;.");
                        groups.add(numbers(m.group(1)));
                        take.add(Integer.parseInt(m.group(2)));
                    }
                    e.add("groups", groups);
                    e.add("take", take);
                }
            }
            if (halves.length == 2) e.add("bonus", numbers(halves[1]));
        } catch (NumberFormatException ex) {
            throw new Error(n, row, "numbers only, separated by spaces.");
        }
        return e;
    }

    private static JsonArray numbers(String s) {
        JsonArray a = new JsonArray();
        for (String p : s.trim().split("[,\\s]+")) if (!p.isBlank()) a.add(Integer.parseInt(p));
        if (a.isEmpty()) throw new NumberFormatException();
        return a;
    }

    /** {game, entries, prizes?} -> text (the inverse of {@link #parse}). */
    public static String write(JsonObject root) {
        PickGame game = Pick.game(root);
        StringBuilder sb = new StringBuilder();
        sb.append("game \"").append(game.title().replace("\"", "'")).append("\" numbers ").append(game.min()).append('-').append(game.max())
                .append(" pick ").append(game.pick()).append(" draw ").append(game.draw()).append('\n');
        if (game.hasBonus()) {
            PickGame.Bonus b = game.bonus();
            sb.append("bonus \"").append(b.label().replace("\"", "'")).append("\" numbers ").append(b.min()).append('-').append(b.max())
                    .append(" pick ").append(b.pick()).append(" draw ").append(b.draw()).append('\n');
        }
        if (root.has("prizes") && root.get("prizes").isJsonObject()) {
            Map<String, JsonElement> sorted = new TreeMap<>((a, b) -> {
                int c = Integer.compare(Integer.parseInt(b.split("\\+")[0]), Integer.parseInt(a.split("\\+")[0]));
                return c != 0 ? c : b.compareTo(a);
            });
            for (var en : root.getAsJsonObject("prizes").entrySet())
                if (en.getKey().matches("\\d+(\\+\\d+)?") && !en.getValue().isJsonNull() && !en.getValue().getAsString().isBlank())
                    sorted.put(en.getKey(), en.getValue());
            sorted.forEach((k, v) -> sb.append("prize ").append(k).append(" = ").append(new BigDecimal(v.getAsString()).stripTrailingZeros().toPlainString()).append('\n'));
        }
        Pick.Request req = Pick.parse(root); // validates the entries
        JsonArray entries = root.getAsJsonArray("entries");
        for (int i = 0; i < entries.size(); i++) {
            JsonObject e = entries.get(i).getAsJsonObject();
            String type = req.entries().get(i).type();
            sb.append("entry ").append(type).append(' ');
            switch (type) {
                case "single", "system" -> sb.append(join(e.getAsJsonArray("numbers")));
                case "wheel" -> sb.append(join(e.getAsJsonArray("numbers"))).append(" guarantee ").append(e.get("guarantee").getAsInt());
                default -> {
                    List<String> parts = new ArrayList<>();
                    JsonArray g = e.getAsJsonArray("groups"), t = e.getAsJsonArray("take");
                    for (int j = 0; j < g.size(); j++) parts.add(join(g.get(j).getAsJsonArray()) + " take " + t.get(j).getAsInt());
                    sb.append(String.join(" ; ", parts));
                }
            }
            if (e.has("bonus") && e.get("bonus").isJsonArray() && !e.getAsJsonArray("bonus").isEmpty()) sb.append(" | ").append(join(e.getAsJsonArray("bonus")));
            sb.append('\n');
        }
        return sb.toString();
    }

    private static String join(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(String.valueOf(e.getAsInt()));
        return String.join(" ", out);
    }
}
