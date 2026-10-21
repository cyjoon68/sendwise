package com.sendwise.api.domain.dispatch;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class PrioritySendQueue {
    public static final Comparator<OutboundItem> PRIORITY = Comparator
            .comparingInt((OutboundItem item) -> OutboundItem.rank(item.purpose()))
            .thenComparingLong(OutboundItem::seq);

    public static final Comparator<OutboundItem> FIFO = Comparator.comparingLong(OutboundItem::seq);

    private final PriorityBlockingQueue<OutboundItem> queue;
    private final AtomicLong sequence = new AtomicLong();

    public PrioritySendQueue(Comparator<OutboundItem> comparator) {
        this.queue = new PriorityBlockingQueue<>(64, comparator);
    }

    public void offer(OutboundItem item) {
        queue.offer(item.withSeq(sequence.incrementAndGet()));
    }

    public void restore(OutboundItem item) {
        queue.offer(item);
    }

    public OutboundItem poll(long timeoutMs) throws InterruptedException {
        return queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
    }

    public OutboundItem peek() {
        return queue.peek();
    }

    public int size() {
        return queue.size();
    }
}
