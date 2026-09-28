package io.wheellab.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collection;

final class Json {

    private Json() {
    }

    static JsonArray ints(Collection<Integer> values) {
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        return a;
    }

    static JsonArray longs(Collection<Long> values) {
        JsonArray a = new JsonArray();
        values.forEach(a::add);
        return a;
    }

    static JsonArray longs(long[] values) {
        JsonArray a = new JsonArray();
        for (long v : values) a.add(v);
        return a;
    }

    static JsonObject error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message == null ? "Unexpected error." : message);
        return o;
    }
}
