package io.wheellab.api;

import io.wheellab.core.LineSource;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Built systems waiting to be read page by page. Only the lazy {@link LineSource} is kept (no lines), and
 * only the most recent {@value #CAPACITY} builds -- an unknown id means "build again".
 */
final class LineStore {

    static final int CAPACITY = 32;

    private final Map<String, LineSource<String>> builds = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LineSource<String>> eldest) {
            return size() > CAPACITY;
        }
    };

    synchronized String put(LineSource<String> lines) {
        String id = UUID.randomUUID().toString();
        builds.put(id, lines);
        return id;
    }

    synchronized LineSource<String> get(String id) {
        return id == null ? null : builds.get(id);
    }
}
