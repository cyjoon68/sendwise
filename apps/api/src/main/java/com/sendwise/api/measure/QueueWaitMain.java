package com.sendwise.api.measure;

import com.sendwise.api.domain.dispatch.OutboundItem;
import com.sendwise.api.domain.dispatch.PrioritySendQueue;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.TimeMeter;

import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;

public final class QueueWaitMain {
    private static final int AD_BACKLOG = 100_000;
    private static final int AUTH_COUNT = 1_000;
    private static final int RATE = 100;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("clock=simulated");
        System.out.println("adBacklog=" + AD_BACKLOG);
        System.out.println("authCount=" + AUTH_COUNT);
        System.out.println("ratePerSecond=" + RATE);
        System.out.println("capacity=" + RATE);
        System.out.println("java=" + Runtime.version());
        System.out.println("maxHeapBytes=" + Runtime.getRuntime().maxMemory());
        run("priority", PrioritySendQueue.PRIORITY);
        run("fifo", PrioritySendQueue.FIFO);
    }

    private static void run(String mode, Comparator<OutboundItem> comparator) throws InterruptedException {
        SimMeter meter = new SimMeter();
        Bucket bucket = Bucket.builder()
                .withCustomTimePrecision(meter)
                .addLimit(Bandwidth.classic(RATE, Refill.greedy(RATE, Duration.ofSeconds(1))))
                .build();
        PrioritySendQueue queue = new PrioritySendQueue(comparator);
        for (int i = 0; i < AD_BACKLOG; i++) {
            queue.offer(item("AD"));
        }
        for (int i = 0; i < AUTH_COUNT; i++) {
            queue.offer(item("AUTH"));
        }
        long[] waits = new long[AUTH_COUNT];
        int auth = 0;
        long wallStart = System.nanoTime();
        while (queue.size() > 0) {
            OutboundItem item = queue.poll(0);
            if (item == null) {
                break;
            }
            while (true) {
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                if (probe.isConsumed()) {
                    break;
                }
                long wait = probe.getNanosToWaitForRefill();
                meter.nanos += wait > 0 ? wait : 1_000_000L;
            }
            if ("AUTH".equals(item.purpose())) {
                waits[auth++] = meter.nanos;
            }
        }
        long wallMillis = (System.nanoTime() - wallStart) / 1_000_000L;
        Arrays.sort(waits, 0, auth);
        int p95Index = (int) Math.ceil(0.95 * auth) - 1;
        System.out.println("mode=" + mode);
        System.out.println("authSeen=" + auth);
        System.out.println("p95Index=" + p95Index);
        System.out.println("p95WaitNanos=" + waits[p95Index]);
        System.out.println("minWaitNanos=" + waits[0]);
        System.out.println("maxWaitNanos=" + waits[auth - 1]);
        System.out.println("wallClockMillis=" + wallMillis);
    }

    private static OutboundItem item(String purpose) {
        return new OutboundItem(0, "SKT", "SMS", "01000000000", "a", null, null, purpose, 0, null);
    }

    private QueueWaitMain() {
    }

    private static final class SimMeter implements TimeMeter {
        private long nanos;

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }
}
