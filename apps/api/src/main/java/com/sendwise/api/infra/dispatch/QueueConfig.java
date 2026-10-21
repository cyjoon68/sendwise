package com.sendwise.api.infra.dispatch;

import com.sendwise.api.domain.dispatch.OutboundItem;
import com.sendwise.api.domain.dispatch.OutboundQueue;
import com.sendwise.api.domain.dispatch.PrioritySendQueue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

public final class QueueConfig {
    private QueueConfig() {
    }

    @Component
    @ConditionalOnProperty(name = "sendwise.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
    public static class CarrierQueues implements OutboundQueue {
        private final ConcurrentHashMap<String, PrioritySendQueue> queues = new ConcurrentHashMap<>();

        @Override
        public void enqueue(OutboundItem item) {
            queue(item.queueKey()).offer(item);
        }

        @Override
        public void restore(OutboundItem item) {
            queue(item.queueKey()).restore(item);
        }

        @Override
        public OutboundItem poll(String queueKey, long timeoutMs) throws InterruptedException {
            return queue(queueKey).poll(timeoutMs);
        }

        private PrioritySendQueue queue(String key) {
            return queues.computeIfAbsent(key, ignored -> new PrioritySendQueue(PrioritySendQueue.PRIORITY));
        }
    }

    @Component
    @ConditionalOnProperty(name = "sendwise.dispatcher-enabled", havingValue = "false")
    public static class DisabledQueue implements OutboundQueue {
        @Override
        public void enqueue(OutboundItem item) {
        }

        @Override
        public void restore(OutboundItem item) {
        }

        @Override
        public OutboundItem poll(String queueKey, long timeoutMs) {
            return null;
        }
    }
}
