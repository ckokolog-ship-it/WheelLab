package io.wheellab.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.wheellab.core.LineSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Function;

/**
 * WheelLab HTTP server: the JSON API under {@code /api/} and, optionally, the built web app.
 * <pre>
 *   java -jar wheellab-server.jar [--port 8090] [--web ../web/dist]
 * </pre>
 * The port can also come from the {@code WHEELLAB_PORT} environment variable. The server keeps no user
 * data: every request carries the whole ticket.
 */
public final class Server {

    static final int DEFAULT_PORT = 8090;
    static final int MAX_BODY = 4 * 1024 * 1024;
    static final int MAX_PAGE = 1000;

    private final HttpServer http;
    private final LineStore store = new LineStore();
    private final Gson gson = new Gson();

    Server(int port, Path web) throws IOException {
        http = HttpServer.create(new InetSocketAddress(port), 0);
        http.setExecutor(Executors.newFixedThreadPool(Math.max(4, Runtime.getRuntime().availableProcessors())));
        MatchXApi matchx = new MatchXApi(store);
        get("/api/health", q -> {
            JsonObject o = new JsonObject();
            o.addProperty("status", "ok");
            o.addProperty("app", "WheelLab");
            return o;
        });
        get("/api/lines", this::page);
        post("/api/matchx/count", matchx::count);
        post("/api/matchx/build", matchx::build);
        post("/api/matchx/check", matchx::check);
        post("/api/matchx/check-lines", matchx::checkLines);
        PickApi pick = new PickApi(store);
        post("/api/pick/count", pick::count);
        post("/api/pick/build", pick::build);
        post("/api/pick/check", pick::check);
        post("/api/pick/check-lines", pick::checkLines);
        post("/api/pick/position", pick::position);
        if (web != null) http.createContext("/", ex -> serveFile(ex, web));
    }

    int port() {
        return http.getAddress().getPort();
    }

    void start() {
        http.start();
    }

    void stop() {
        http.stop(0);
    }

    public static void main(String[] args) throws IOException {
        int port = System.getenv("WHEELLAB_PORT") != null ? Integer.parseInt(System.getenv("WHEELLAB_PORT")) : DEFAULT_PORT;
        Path web = null;
        for (int i = 0; i + 1 < args.length; i += 2) {
            switch (args[i]) {
                case "--port" -> port = Integer.parseInt(args[i + 1]);
                case "--web" -> web = Path.of(args[i + 1]).toAbsolutePath().normalize();
                default -> throw new IllegalArgumentException("Unknown option " + args[i] + " (use --port N, --web DIR).");
            }
        }
        Server s = new Server(port, web);
        s.start();
        System.out.println("WheelLab server on http://localhost:" + s.port() + (web != null ? " (web app from " + web + ")" : ""));
    }

    /** GET /api/lines?id=..&start=0&size=100 -> {total, start, lines:[[..],..]} */
    private JsonObject page(Map<String, String> q) {
        LineSource<String> lines = store.get(q.get("id"));
        if (lines == null) throw new NotFound("Unknown or expired id -- build the system again.");
        long start = q.containsKey("start") ? Long.parseLong(q.get("start")) : 0;
        int size = q.containsKey("size") ? Integer.parseInt(q.get("size")) : 100;
        if (start < 0 || size < 1 || size > MAX_PAGE) throw new IllegalArgumentException("start >= 0 and size 1 to " + MAX_PAGE + ".");
        JsonObject o = new JsonObject();
        o.addProperty("total", lines.size());
        o.addProperty("start", start);
        JsonArray arr = new JsonArray();
        for (long i = start; i < Math.min(lines.size(), start + size); i++) {
            JsonArray line = new JsonArray();
            for (String s : lines.get(i)) line.add(s);
            arr.add(line);
        }
        o.add("lines", arr);
        return o;
    }

    private void get(String path, Function<Map<String, String>, JsonObject> handler) {
        http.createContext(path, ex -> handle(ex, "GET", () -> handler.apply(query(ex))));
    }

    private void post(String path, Function<JsonObject, JsonObject> handler) {
        http.createContext(path, ex -> handle(ex, "POST", () -> {
            JsonElement body;
            try {
                body = JsonParser.parseString(new String(readBody(ex), StandardCharsets.UTF_8));
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("The request is not valid JSON.");
            }
            if (!body.isJsonObject()) throw new IllegalArgumentException("Expected a JSON object.");
            return handler.apply(body.getAsJsonObject());
        }));
    }

    private interface Call {
        JsonObject run() throws IOException;
    }

    private static final class NotFound extends RuntimeException {
        NotFound(String message) {
            super(message);
        }
    }

    private void handle(HttpExchange ex, String method, Call call) throws IOException {
        try (ex) {
            cors(ex);
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                ex.sendResponseHeaders(204, -1);
                return;
            }
            if (!method.equals(ex.getRequestMethod())) {
                send(ex, 405, Json.error("Use " + method + "."));
                return;
            }
            try {
                send(ex, 200, call.run());
            } catch (NotFound e) {
                send(ex, 404, Json.error(e.getMessage()));
            } catch (IllegalArgumentException | IllegalStateException | ArithmeticException | IndexOutOfBoundsException e) {
                send(ex, 400, Json.error(e instanceof ArithmeticException ? "The system is too large to count." : e.getMessage()));
            } catch (RuntimeException e) {
                e.printStackTrace();
                send(ex, 500, Json.error("Unexpected error: " + e));
            }
        }
    }

    private static void cors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private void send(HttpExchange ex, int status, JsonObject body) throws IOException {
        byte[] bytes = gson.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static byte[] readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY + 1);
            if (data.length > MAX_BODY) throw new IllegalArgumentException("The request is too large.");
            return data;
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) return out;
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0)
                out.put(URLDecoder.decode(part.substring(0, eq), StandardCharsets.UTF_8), URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    private static final Map<String, String> TYPES = Map.of("html", "text/html; charset=utf-8", "js", "text/javascript",
            "css", "text/css", "svg", "image/svg+xml", "png", "image/png", "ico", "image/x-icon", "json", "application/json");

    /** The built web app; unknown paths fall back to index.html. */
    private static void serveFile(HttpExchange ex, Path root) throws IOException {
        try (ex) {
            String path = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);
            Path file = root.resolve(path.replaceFirst("^/+", "")).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) file = root.resolve("index.html");
            if (!Files.isRegularFile(file)) {
                ex.sendResponseHeaders(404, -1);
                return;
            }
            String name = file.getFileName().toString();
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            byte[] bytes = Files.readAllBytes(file);
            ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
            ex.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
