package com.sendwise.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CarrierRoundTripTest {
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");
    private static final ConcurrentLinkedQueue<String> BODIES = new ConcurrentLinkedQueue<>();
    private static final AtomicReference<String> MODE = new AtomicReference<>("202");
    private static final HttpServer STUB;
    private static final int STUB_PORT;
    private static final ObjectMapper JSON = new ObjectMapper();

    static {
        MYSQL.start();
        try {
            STUB = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            STUB_PORT = STUB.getAddress().getPort();
            STUB.createContext("/send", exchange -> {
                String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                BODIES.add(body);
                String mode = MODE.get();
                int status = "400".equals(mode) ? 400 : "429".equals(mode) ? 429 : 202;
                String json = switch (status) {
                    case 400 -> "{\"accepted\":false,\"reason\":\"MISSING_BODY\"}";
                    case 429 -> "{\"accepted\":false,\"reason\":\"TPS_EXCEEDED\"}";
                    default -> "{\"accepted\":true}";
                };
                byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.close();
            });
            STUB.start();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("sendwise.dispatcher-enabled", () -> "true");
        registry.add("sendwise.carrier-base-url", () -> "http://127.0.0.1:" + STUB_PORT);
        registry.add("sendwise.carrier-rate-per-second", () -> "20");
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sendThenCallbackUpdatesStatus() throws Exception {
        MODE.set("202");
        String phone = "01012345670";
        long campaignId = create("ALIMTALK", "NONE", phone, "WELCOME");
        JsonNode sent = awaitBody(phone);
        long messageId = jdbc.queryForObject(
                "select id from message where campaign_id = ? and recipient = ?",
                Long.class,
                campaignId,
                phone
        );
        assertEquals(Long.toString(messageId), sent.get("messageId").asText());
        assertEquals("SKT", sent.get("carrier").asText());
        assertEquals(phone, sent.get("recipient").asText());
        assertEquals("안녕", sent.get("body").asText());
        assertEquals("ALIMTALK", sent.get("channel").asText());
        assertEquals("WELCOME", sent.get("templateCode").asText());
        assertTrue(sent.get("variables").isNull());

        ResponseEntity<Map> callback = rest.postForEntity("/carrier/SKT/results", Map.of(
                "messageId", Long.toString(messageId),
                "carrier", "SKT",
                "recipient", phone,
                "channel", "ALIMTALK",
                "carrierCode", "0",
                "commonCode", "ETC"
        ), Map.class);
        assertEquals(200, callback.getStatusCode().value());
        assertEquals("DELIVERED", callback.getBody().get("status"));
        assertEquals("SUCCESS", callback.getBody().get("commonCode"));

        ResponseEntity<Map> again = rest.postForEntity("/carrier/SKT/results", Map.of(
                "messageId", Long.toString(messageId),
                "carrierCode", "101",
                "commonCode", "INVALID_NUMBER"
        ), Map.class);
        assertEquals("DELIVERED", again.getBody().get("status"));
        assertEquals("SUCCESS", again.getBody().get("commonCode"));

        Map<?, ?> page = rest.getForObject("/v1/campaigns/" + campaignId + "/messages?status=DELIVERED", Map.class);
        Map<?, ?> item = (Map<?, ?>) ((List<?>) page.get("items")).get(0);
        assertEquals("DELIVERED", item.get("status"));
        assertEquals("SUCCESS", item.get("commonCode"));
    }

    @Test
    void tpsExceededStaysQueuedUntilAccepted() throws Exception {
        MODE.set("429");
        String phone = "01012345672";
        long campaignId = create("SMS", "NONE", phone, null);
        awaitHits(phone, 2);
        assertEquals("QUEUED", status(campaignId, phone));
        MODE.set("202");
        awaitStatus(campaignId, phone, "SENT");
    }

    @Test
    void validationRejectMarksFailedOnce() throws Exception {
        MODE.set("400");
        String phone = "01012345671";
        long before = jdbc.queryForObject("select amount from balance where client_id = 1", Long.class);
        long campaignId = create("SMS", "NONE", phone, null);
        awaitStatus(campaignId, phone, "FAILED");
        Thread.sleep(400);
        assertEquals(1, hits(phone));
        assertEquals(before, jdbc.queryForObject("select amount from balance where client_id = 1", Long.class));
        MODE.set("202");
    }

    private long create(String channel, String fallback, String phone, String template) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("clientId", 1L);
        body.put("channel", channel);
        body.put("purpose", "NORMAL");
        body.put("body", "안녕");
        body.put("fallback", fallback);
        body.put("senderNumber", "0212345678");
        body.put("recipients", List.of(phone));
        if (template != null) {
            body.put("templateCode", template);
        }
        ResponseEntity<Map> created = rest.postForEntity("/v1/campaigns", body, Map.class);
        assertEquals(201, created.getStatusCode().value(), String.valueOf(created.getBody()));
        return ((Number) created.getBody().get("id")).longValue();
    }

    private JsonNode awaitBody(String phone) throws Exception {
        String raw = awaitRaw(phone, 1);
        assertNotNull(raw);
        return JSON.readTree(raw);
    }

    private void awaitHits(String phone, int count) throws Exception {
        awaitRaw(phone, count);
    }

    private String awaitRaw(String phone, int count) throws Exception {
        for (int i = 0; i < 50; i++) {
            List<String> matched = BODIES.stream().filter(body -> body.contains(phone)).toList();
            if (matched.size() >= count) {
                return matched.get(matched.size() - 1);
            }
            Thread.sleep(100);
        }
        throw new AssertionError("no send for " + phone + " saw " + BODIES);
    }

    private int hits(String phone) {
        return (int) BODIES.stream().filter(body -> body.contains(phone)).count();
    }

    private String status(long campaignId, String phone) {
        return jdbc.queryForObject(
                "select status from message where campaign_id = ? and recipient = ?",
                String.class,
                campaignId,
                phone
        );
    }

    private void awaitStatus(long campaignId, String phone, String expected) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (expected.equals(status(campaignId, phone))) {
                return;
            }
            Thread.sleep(100);
        }
        assertEquals(expected, status(campaignId, phone));
    }
}
