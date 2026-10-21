package com.sendwise.carriermock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendwise.carriermock.domain.Carrier;
import com.sendwise.carriermock.domain.CarrierCodeBook;
import com.sendwise.carriermock.domain.CommonCode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SendEndpointTest {

    private static final CallbackStub stub = CallbackStub.start();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private CarrierCodeBook codes;

    @DynamicPropertySource
    static void callbackTarget(DynamicPropertyRegistry registry) {
        registry.add("carrier-mock.callback-base-url", stub::baseUrl);
    }

    @AfterAll
    static void stopStub() {
        stub.close();
    }

    @Test
    void sendPostsTheSharedSuccessCode() throws Exception {
        relax("SKT");
        String messageId = "sms-success";

        ResponseEntity<String> response = post(send(messageId, "SKT", "SMS", "hello", null, null));

        assertEquals(202, response.getStatusCode().value());
        JsonNode receipt = mapper.readTree(response.getBody());
        assertTrue(receipt.get("accepted").asBoolean());
        assertTrue(receipt.path("reason").isMissingNode(), response.getBody());
        JsonNode callback = awaitCallback(messageId);
        assertEquals("POST", hit(messageId).method());
        assertEquals("/carrier/SKT/results", hit(messageId).path());
        assertEquals("SUCCESS", callback.get("commonCode").asText());
        assertEquals(codes.carrierCode(Carrier.SKT, CommonCode.SUCCESS), callback.get("carrierCode").asText());
    }

    @Test
    void secondSendOverTheLimitReturns429() throws Exception {
        relax("LGU");
        put("LGU", Map.of("maxTps", 1));

        ResponseEntity<String> first = post(send("lgu-1", "LGU", "SMS", "hello", null, null));
        ResponseEntity<String> second = post(send("lgu-2", "LGU", "SMS", "hello", null, null));

        assertEquals(202, first.getStatusCode().value());
        assertEquals(429, second.getStatusCode().value());
        assertEquals("TPS_EXCEEDED", mapper.readTree(second.getBody()).get("reason").asText());
        awaitCallback("lgu-1");
        assertTrue(stub.hits().stream().noneMatch(hit -> hit.body().contains("\"lgu-2\"")));
    }

    @Test
    void highFailurePostsTheSharedEtcCode() throws Exception {
        relax("KT");
        put("KT", Map.of("failureRatio", Map.of(
                "INVALID_NUMBER", 0,
                "OPTED_OUT", 0,
                "DEVICE_OFF", 0,
                "SPAM_BLOCKED", 0,
                "ETC", 1)));

        ResponseEntity<String> response = post(send("kt-etc", "KT", "LMS", "long body", null, null));

        assertEquals(202, response.getStatusCode().value());
        JsonNode callback = awaitCallback("kt-etc");
        assertEquals("ETC", callback.get("commonCode").asText());
        assertEquals(codes.carrierCode(Carrier.KT, CommonCode.ETC), callback.get("carrierCode").asText());
    }

    @Test
    void templateMismatchPostsTheSharedEtcCode() throws Exception {
        relax("SKT");
        Map<String, String> variables = Map.of("name", "홍길동", "orderNo", "A1");

        ResponseEntity<String> response = post(send(
                "tpl-1",
                "SKT",
                "ALIMTALK",
                "다른 본문",
                "ORDER_SHIPPED",
                variables));

        assertEquals(202, response.getStatusCode().value());
        JsonNode callback = awaitCallback("tpl-1");
        assertEquals("ALIMTALK", callback.get("channel").asText());
        assertEquals("ETC", callback.get("commonCode").asText());
        assertEquals(codes.carrierCode(Carrier.SKT, CommonCode.ETC), callback.get("carrierCode").asText());
    }

    @Test
    void callbackLossSendsNothing() throws Exception {
        relax("KT");
        put("KT", Map.of("callbackLossRatio", 1));

        ResponseEntity<String> response = post(send("kt-lost", "KT", "SMS", "hello", null, null));

        assertEquals(202, response.getStatusCode().value());
        Thread.sleep(400);
        assertTrue(stub.hits().stream().noneMatch(hit -> hit.body().contains("\"kt-lost\"")));
    }

    @Test
    void missingMessageIdReturns400() throws Exception {
        ResponseEntity<String> response = post(send(" ", "SKT", "SMS", "hello", null, null));

        assertEquals(400, response.getStatusCode().value());
        assertEquals("MISSING_MESSAGE_ID", mapper.readTree(response.getBody()).get("reason").asText());
    }

    private void relax(String carrier) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("maxTps", 100);
        body.put("callbackDelayMillis", 0);
        body.put("callbackLossRatio", 0);
        body.put("latency", Map.of("distribution", "fixed", "minMillis", 0, "maxMillis", 0));
        body.put("failureRatio", Map.of(
                "INVALID_NUMBER", 0,
                "OPTED_OUT", 0,
                "DEVICE_OFF", 0,
                "SPAM_BLOCKED", 0,
                "ETC", 0));
        put(carrier, body);
    }

    private void put(String carrier, Map<String, ?> body) {
        ResponseEntity<String> response = rest.exchange(
                "/admin/carriers/" + carrier,
                HttpMethod.PUT,
                new HttpEntity<>(body),
                String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
    }

    private ResponseEntity<String> post(Map<String, Object> body) {
        return rest.postForEntity("/send", body, String.class);
    }

    private static Map<String, Object> send(
            String messageId,
            String carrier,
            String channel,
            String body,
            String templateCode,
            Map<String, String> variables) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("messageId", messageId);
        request.put("carrier", carrier);
        request.put("recipient", "01012345678");
        request.put("body", body);
        request.put("channel", channel);
        if (templateCode != null) {
            request.put("templateCode", templateCode);
        }
        if (variables != null) {
            request.put("variables", variables);
        }
        return request;
    }

    private JsonNode awaitCallback(String messageId) throws Exception {
        long deadline = System.nanoTime() + 3_000_000_000L;
        while (System.nanoTime() < deadline) {
            for (CallbackStub.Hit hit : stub.hits()) {
                if (hit.body().contains("\"" + messageId + "\"")) {
                    return mapper.readTree(hit.body());
                }
            }
            Thread.sleep(20);
        }
        throw new AssertionError("no callback for " + messageId);
    }

    private CallbackStub.Hit hit(String messageId) {
        return stub.hits().stream()
                .filter(hit -> hit.body().contains("\"" + messageId + "\""))
                .findFirst()
                .orElseThrow();
    }
}
