package com.essenza.draco.modules.sales.domain.model.storefront;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Límite de intentos por clave (p. ej. IP del comprador) en una ventana deslizante, en memoria. Protege el
 * checkout público de abuso (reservas masivas de stock); no reemplaza un WAF. Hasta {@code maxKeys} claves.
 */
public final class RateLimiter {

    private final int limit;
    private final Duration window;
    private final int maxKeys;
    private final Map<String, Deque<Instant>> hits = new LinkedHashMap<>();

    public RateLimiter(int limit, Duration window, int maxKeys) {
        this.limit = Math.max(1, limit);
        this.window = window;
        this.maxKeys = Math.max(100, maxKeys);
    }

    /** @return true si se permite el intento (y lo registra) */
    public synchronized boolean tryAcquire(String key, Instant now) {
        Instant from = now.minus(window);
        Deque<Instant> times = hits.computeIfAbsent(key == null ? "?" : key, k -> new ArrayDeque<>());
        while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
            times.pollFirst();
        }
        if (times.size() >= limit) {
            return false;
        }
        times.addLast(now);
        if (hits.size() > maxKeys) {
            Iterator<Map.Entry<String, Deque<Instant>>> it = hits.entrySet().iterator();
            while (it.hasNext() && hits.size() > maxKeys) {
                Map.Entry<String, Deque<Instant>> entry = it.next();
                if (!entry.getKey().equals(key)) {
                    it.remove();
                }
            }
        }
        return true;
    }
}
