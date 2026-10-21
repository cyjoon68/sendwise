package com.sendwise.carriermock.infra;

import com.sendwise.carriermock.domain.CallbackMessage;
import com.sendwise.carriermock.domain.ResultSink;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

public final class HttpResultSink implements ResultSink {

    private static final Logger log = LoggerFactory.getLogger(HttpResultSink.class);

    private final String baseUrl;
    private final RestClient client;
    private final ScheduledExecutorService scheduler;

    public HttpResultSink(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("MISSING_CALLBACK_BASE_URL");
        }
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(2));
        requestFactory.setReadTimeout(Duration.ofSeconds(2));
        this.client = RestClient.builder().requestFactory(requestFactory).build();
        this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "carrier-callback");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Override
    public void schedule(long delayMillis, CallbackMessage message) {
        scheduler.schedule(() -> post(message), Math.max(delayMillis, 0), TimeUnit.MILLISECONDS);
    }

    public void close() {
        scheduler.shutdownNow();
    }

    private void post(CallbackMessage message) {
        URI uri = URI.create(baseUrl + "/carrier/" + message.carrier().name() + "/results");
        try {
            client.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(message)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RuntimeException ex) {
            // shortcut: callback HTTP failure is logged and dropped, add retry when the api contract defines it.
            log.warn("callback failed messageId={} carrier={}", message.messageId(), message.carrier(), ex);
        }
    }
}
