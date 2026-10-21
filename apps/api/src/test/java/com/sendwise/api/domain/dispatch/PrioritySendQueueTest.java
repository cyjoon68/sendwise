package com.sendwise.api.domain.dispatch;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrioritySendQueueTest {
    @Test
    void authLeavesBeforeAd() throws Exception {
        PrioritySendQueue queue = new PrioritySendQueue(PrioritySendQueue.PRIORITY);
        queue.offer(item("AD", 1));
        queue.offer(item("NORMAL", 2));
        queue.offer(item("AUTH", 3));
        assertEquals("AUTH", queue.poll(100).purpose());
        assertEquals("NORMAL", queue.poll(100).purpose());
        assertEquals("AD", queue.poll(100).purpose());
    }

    @Test
    void fifoKeepsArrivalOrder() throws Exception {
        PrioritySendQueue queue = new PrioritySendQueue(PrioritySendQueue.FIFO);
        queue.offer(item("AD", 1));
        queue.offer(item("AUTH", 2));
        assertEquals("AD", queue.poll(100).purpose());
        assertEquals("AUTH", queue.poll(100).purpose());
    }

    private OutboundItem item(String purpose, long id) {
        return new OutboundItem(id, "SKT", "SMS", "01012345678", "a", null, purpose, 0, null);
    }
}
