package com.sendwise.api.domain.dispatch;

public interface OutboundQueue {
    void enqueue(OutboundItem item);

    void restore(OutboundItem item);

    OutboundItem poll(String queueKey, long timeoutMs) throws InterruptedException;
}
