package com.sendwise.api.infra.dispatch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sendwise.api.application.ResultService;
import com.sendwise.api.domain.dispatch.OutboundItem;
import com.sendwise.api.domain.dispatch.OutboundQueue;
import com.sendwise.api.infra.persistence.MessageRepository;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
@ConditionalOnProperty(name = "sendwise.dispatcher-enabled", havingValue = "true", matchIfMissing = true)
public class CarrierDispatcher implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(CarrierDispatcher.class);
    private static final List<String> KEYS = List.of("SKT", "KT", "LGU", "KAKAO");

    private final OutboundQueue queue;
    private final ResultService results;
    private final MessageRepository messages;
    private final TransactionTemplate tx;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String baseUrl;
    private final int rate;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private volatile boolean running;

    public CarrierDispatcher(
            OutboundQueue queue,
            ResultService results,
            MessageRepository messages,
            PlatformTransactionManager manager,
            ObjectMapper mapper,
            Clock clock,
            @Value("${sendwise.carrier-base-url:http://localhost:8081}") String baseUrl,
            @Value("${sendwise.carrier-rate-per-second:50}") int rate
    ) {
        this.queue = queue;
        this.results = results;
        this.messages = messages;
        this.tx = new TransactionTemplate(manager);
        this.mapper = mapper;
        this.clock = clock;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.rate = Math.max(1, rate);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        List<OutboundItem> queued = tx.execute(status -> messages.findByStatus("QUEUED").stream()
                .map(message -> new OutboundItem(
                        message.getId(),
                        message.getCarrier(),
                        message.getChannel(),
                        message.getRecipient(),
                        message.getBody(),
                        message.getTemplateCode(),
                        message.getPurpose(),
                        0,
                        message.getCampaign().getScheduledAt()
                ))
                .toList());
        if (queued != null) {
            queued.forEach(queue::enqueue);
        }
        for (String key : KEYS) {
            Bucket bucket = Bucket.builder()
                    .addLimit(Bandwidth.classic(rate, Refill.greedy(rate, Duration.ofSeconds(1))))
                    .build();
            Thread thread = new Thread(() -> loop(key, bucket), "carrier-" + key);
            thread.setDaemon(true);
            thread.start();
            threads.add(thread);
        }
    }

    private void loop(String key, Bucket bucket) {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                OutboundItem item = queue.poll(key, 200);
                if (item == null) {
                    continue;
                }
                if (item.notBefore() != null && clock.instant().isBefore(item.notBefore())) {
                    queue.restore(item);
                    Thread.sleep(200);
                    continue;
                }
                ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
                if (!probe.isConsumed()) {
                    queue.restore(item);
                    Thread.sleep(Math.min(50, Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000)));
                    continue;
                }
                if (!messages.existsByIdAndStatus(item.messageId(), "QUEUED")) {
                    continue;
                }
                Outcome outcome = send(item);
                if (outcome == Outcome.SENT) {
                    results.markSent(item.messageId());
                } else if (outcome == Outcome.RETRY) {
                    queue.restore(item);
                    Thread.sleep(100);
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (Exception exception) {
                log.warn("carrier send failed: {}", exception.toString());
            }
        }
    }

    private Outcome send(OutboundItem item) {
        try {
            ObjectNode payload = mapper.createObjectNode();
            payload.put("messageId", Long.toString(item.messageId()));
            payload.put("carrier", item.carrier());
            payload.put("recipient", item.recipient());
            payload.put("body", item.body());
            payload.put("channel", item.channel());
            if (item.templateCode() == null) {
                payload.putNull("templateCode");
            } else {
                payload.put("templateCode", item.templateCode());
            }
            payload.putNull("variables");
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/send"))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status == 202) {
                return Outcome.SENT;
            }
            if (status == 429) {
                return Outcome.RETRY;
            }
            if (status == 400) {
                results.markRejected(item.messageId());
                return Outcome.REJECTED;
            }
            log.warn("carrier send status {}", status);
            return Outcome.RETRY;
        } catch (Exception exception) {
            log.warn("carrier send failed: {}", exception.toString());
            return Outcome.RETRY;
        }
    }

    private enum Outcome {
        SENT, RETRY, REJECTED
    }

    @Override
    public void stop() {
        running = false;
        threads.forEach(Thread::interrupt);
        threads.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
