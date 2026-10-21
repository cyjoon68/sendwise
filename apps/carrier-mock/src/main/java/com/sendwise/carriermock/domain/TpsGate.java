package com.sendwise.carriermock.domain;

import java.util.ArrayDeque;
import java.util.EnumMap;
import java.util.Map;

public final class TpsGate {

    public static final long WINDOW_MILLIS = 1_000L;

    private final Map<Carrier, ArrayDeque<Long>> hits = new EnumMap<>(Carrier.class);

    public TpsGate() {
        for (Carrier carrier : Carrier.values()) {
            hits.put(carrier, new ArrayDeque<>());
        }
    }

    public boolean tryAcquire(Carrier carrier, int maxTps, long nowMillis) {
        if (maxTps <= 0) {
            return false;
        }
        ArrayDeque<Long> window = hits.get(carrier);
        synchronized (window) {
            while (!window.isEmpty() && nowMillis - window.peekFirst() >= WINDOW_MILLIS) {
                window.removeFirst();
            }
            if (window.size() >= maxTps) {
                return false;
            }
            window.addLast(nowMillis);
            return true;
        }
    }
}
