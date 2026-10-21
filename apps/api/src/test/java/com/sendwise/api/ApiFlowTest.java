package com.sendwise.api;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiFlowTest {
    private static final String AD_BODY = "(광고)센드와이즈\n본문\n무료수신거부 080-000-0000";

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        MysqlSuite.register(registry);
    }

    @Autowired
    TestRestTemplate rest;

    @Autowired
    JdbcTemplate jdbc;

    long clientId;

    @BeforeEach
    void client() {
        jdbc.update("insert into client(name) values ('flow')");
        clientId = jdbc.queryForObject("select max(id) from client", Long.class);
        jdbc.update("insert into balance(client_id, amount) values (?, ?)", clientId, 1000);
        jdbc.update("insert into sender_number(client_id, phone, status, created_at) values (?, '0212345678', 'APPROVED', utc_timestamp(6))", clientId);
        jdbc.update(
                "insert into alimtalk_template(client_id, template_code, body, variables_json, status) values (?, 'WELCOME', '안녕', '[]', 'APPROVED')",
                clientId
        );
    }

    @Test
    void rulesExposeSharedShape() {
        Map<?, ?> rules = rest.getForObject("/v1/rules", Map.class);
        Map<?, ?> sms = (Map<?, ?>) rules.get("sms");
        Map<?, ?> hours = (Map<?, ?>) ((Map<?, ?>) rules.get("ad")).get("blockedHours");
        Map<?, ?> price = (Map<?, ?>) rules.get("unitPrice");
        assertEquals(90, ((Number) sms.get("maxBytes")).intValue());
        assertEquals("21:00", hours.get("from"));
        assertEquals(Boolean.TRUE, hours.get("fromInclusive"));
        assertEquals(Boolean.FALSE, hours.get("toInclusive"));
        assertEquals("Asia/Seoul", hours.get("zone"));
        assertEquals(9, ((Number) price.get("SMS")).intValue());
    }

    @Test
    void rejectsAdInBlockedHoursAndMissingPrefix() {
        ResponseEntity<Map> blocked = post(ad("2026-10-22T21:00:00+09:00", AD_BODY));
        assertEquals(400, blocked.getStatusCode().value());
        assertEquals("AD_BLOCKED_HOURS", blocked.getBody().get("error"));

        ResponseEntity<Map> prefix = post(ad("2026-10-22T20:59:00+09:00", "센드와이즈\n본문\n무료수신거부 080-000-0000"));
        assertEquals(400, prefix.getStatusCode().value());
        assertEquals("AD_PREFIX_REQUIRED", prefix.getBody().get("error"));

        ResponseEntity<Map> allowed = post(ad("2026-10-22T20:59:00+09:00", AD_BODY));
        assertEquals(201, allowed.getStatusCode().value());
    }

    @Test
    void rejectsUnapprovedSender() {
        jdbc.update("insert into sender_number(client_id, phone, status, created_at) values (?, '0299998888', 'REVIEWING', utc_timestamp(6))", clientId);
        Map<String, Object> body = smsBody("01012345678");
        body.put("senderNumber", "0299998888");
        ResponseEntity<Map> response = post(body);
        assertEquals(400, response.getStatusCode().value());
        assertEquals("SENDER_NOT_APPROVED", response.getBody().get("error"));
    }

    @Test
    void shortfallWhenBalanceIsTooLow() {
        jdbc.update("update balance set amount = 1 where client_id = ?", clientId);
        ResponseEntity<Map> response = post(smsBody("01012345671"));
        assertEquals(402, response.getStatusCode().value());
        assertEquals("INSUFFICIENT_BALANCE", response.getBody().get("error"));
        assertEquals(8, ((Number) response.getBody().get("shortfall")).intValue());
        assertEquals(1L, jdbc.queryForObject("select amount from balance where client_id = ?", Long.class, clientId));
    }

    @Test
    void alimtalkFailureCreatesPricedFallbackAndRefunds() {
        Map<String, Object> created = post(Map.of(
                "clientId", clientId,
                "channel", "ALIMTALK",
                "purpose", "NORMAL",
                "body", "안녕",
                "fallback", "SAME",
                "senderNumber", "0212345678",
                "templateCode", "WELCOME",
                "recipients", List.of("01012345670")
        )).getBody();
        long campaignId = ((Number) created.get("id")).longValue();
        assertEquals(16, ((Number) created.get("estimatedCost")).intValue());
        Map<?, ?> page = rest.getForObject("/v1/campaigns/" + campaignId + "/messages?page=0", Map.class);
        Map<?, ?> original = (Map<?, ?>) ((List<?>) page.get("items")).get(0);
        long messageId = ((Number) original.get("id")).longValue();
        assertEquals("SKT", original.get("carrier"));

        ResponseEntity<Map> failed = rest.postForEntity("/carrier/SKT/results", Map.of(
                "messageId", messageId,
                "carrierCode", "101",
                "commonCode", "SUCCESS"
        ), Map.class);
        assertEquals(200, failed.getStatusCode().value());
        assertEquals("FAILED", failed.getBody().get("status"));
        assertEquals("INVALID_NUMBER", failed.getBody().get("commonCode"));
        assertNotNull(failed.getBody().get("fallbackMessageId"));
        long fallbackId = ((Number) failed.getBody().get("fallbackMessageId")).longValue();
        assertEquals(991L, jdbc.queryForObject("select amount from balance where client_id = ?", Long.class, clientId));

        ResponseEntity<Map> delivered = rest.postForEntity("/carrier/SKT/results", Map.of("messageId", String.valueOf(fallbackId), "carrierCode", "0"), Map.class);
        assertEquals("DELIVERED", delivered.getBody().get("status"));
        assertEquals("SUCCESS", delivered.getBody().get("commonCode"));
        assertEquals(991L, jdbc.queryForObject("select amount from balance where client_id = ?", Long.class, clientId));

        ResponseEntity<Map> again = rest.postForEntity("/carrier/SKT/results", Map.of("messageId", fallbackId, "carrierCode", "0"), Map.class);
        assertEquals("DELIVERED", again.getBody().get("status"));
        assertEquals(991L, jdbc.queryForObject("select amount from balance where client_id = ?", Long.class, clientId));

        Map<?, ?> failedPage = rest.getForObject("/v1/campaigns/" + campaignId + "/messages?status=FAILED", Map.class);
        Map<?, ?> failedItem = (Map<?, ?>) ((List<?>) failedPage.get("items")).get(0);
        assertEquals("없는 번호라 전달하지 못했어요", failedItem.get("explanation"));
        assertEquals("번호를 확인한 뒤 다시 등록해 주세요", failedItem.get("actionHint"));
    }

    @Test
    void unknownCarrierCodeStaysNullAndRefunds() {
        Map<String, Object> created = post(smsBody("01012345671")).getBody();
        long campaignId = ((Number) created.get("id")).longValue();
        Map<?, ?> page = rest.getForObject("/v1/campaigns/" + campaignId + "/messages", Map.class);
        long messageId = ((Number) ((Map<?, ?>) ((List<?>) page.get("items")).get(0)).get("id")).longValue();
        ResponseEntity<Map> result = rest.postForEntity("/carrier/KT/results", Map.of("messageId", messageId, "carrierCode", "12345", "commonCode", "SUCCESS"), Map.class);
        assertEquals("FAILED", result.getBody().get("status"));
        assertNull(result.getBody().get("commonCode"));
        assertEquals(1000L, jdbc.queryForObject("select amount from balance where client_id = ?", Long.class, clientId));
    }

    @Test
    void senderRestFollowsReviewTransitions() {
        ResponseEntity<Map> created = rest.postForEntity("/v1/sender-numbers", Map.of("clientId", clientId, "phone", "02-1111-2222"), Map.class);
        assertEquals(201, created.getStatusCode().value());
        assertEquals("SUBMITTED", created.getBody().get("status"));
        long id = ((Number) created.getBody().get("id")).longValue();
        ResponseEntity<Map> jump = rest.exchange("/v1/sender-numbers/" + id, HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "APPROVED")), Map.class);
        assertEquals(409, jump.getStatusCode().value());
        rest.exchange("/v1/sender-numbers/" + id, HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "REVIEWING")), Map.class);
        ResponseEntity<Map> approved = rest.exchange("/v1/sender-numbers/" + id, HttpMethod.PATCH, new HttpEntity<>(Map.of("status", "APPROVED")), Map.class);
        assertEquals("APPROVED", approved.getBody().get("status"));
        rest.delete("/v1/sender-numbers/" + id);
        assertEquals(404, rest.getForEntity("/v1/sender-numbers/" + id, Map.class).getStatusCode().value());
    }

    @Test
    void jspListsSenderNumbers() {
        String html = rest.getForObject("/sender-numbers?clientId=" + clientId, String.class);
        assertTrue(html.contains("발신번호"));
        assertTrue(html.contains("0212345678"));
        assertTrue(html.contains("승인"));
    }

    @Test
    void exportWritesXlsxAndStatsGroupByCarrier() throws Exception {
        Map<String, Object> created = post(smsBody("01012345672")).getBody();
        long campaignId = ((Number) created.get("id")).longValue();
        ResponseEntity<Map> job = rest.postForEntity("/v1/campaigns/" + campaignId + "/exports", Map.of(), Map.class);
        assertEquals(202, job.getStatusCode().value());
        long exportId = ((Number) job.getBody().get("id")).longValue();
        String status = "PENDING";
        for (int i = 0; i < 50 && !"DONE".equals(status); i++) {
            Thread.sleep(100);
            status = (String) rest.getForObject("/v1/exports/" + exportId, Map.class).get("status");
        }
        assertEquals("DONE", status);
        ResponseEntity<Resource> file = rest.exchange("/v1/exports/" + exportId + "/file", HttpMethod.GET, HttpEntity.EMPTY, Resource.class);
        try (InputStream in = file.getBody().getInputStream(); XSSFWorkbook workbook = new XSSFWorkbook(in)) {
            assertEquals("수신번호", workbook.getSheetAt(0).getRow(0).getCell(1).getStringCellValue());
            assertEquals("01012345672", workbook.getSheetAt(0).getRow(1).getCell(1).getStringCellValue());
        }
        Map<?, ?> stats = rest.getForObject("/v1/stats?from=2020-01-01&to=2030-01-01&groupBy=carrier", Map.class);
        assertEquals("carrier", stats.get("groupBy"));
        assertTrue(((List<?>) stats.get("groups")).size() >= 1);
    }

    @Test
    void corsAllowsFrontendOrigin() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Origin", "http://localhost:5173");
        ResponseEntity<String> response = rest.exchange("/v1/rules", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals("http://localhost:5173", response.getHeaders().getFirst("Access-Control-Allow-Origin"));
    }

    private Map<String, Object> ad(String scheduledAt, String body) {
        return new java.util.LinkedHashMap<>(Map.of(
                "clientId", clientId,
                "channel", "SMS",
                "purpose", "AD",
                "body", body,
                "fallback", "NONE",
                "senderNumber", "0212345678",
                "companyName", "센드와이즈",
                "scheduledAt", scheduledAt,
                "recipients", List.of("01012345673")
        ));
    }

    private Map<String, Object> smsBody(String phone) {
        return new java.util.LinkedHashMap<>(Map.of(
                "clientId", clientId,
                "channel", "SMS",
                "purpose", "NORMAL",
                "body", "ping",
                "fallback", "NONE",
                "senderNumber", "0212345678",
                "recipients", List.of(phone)
        ));
    }

    private ResponseEntity<Map> post(Map<String, Object> body) {
        return rest.postForEntity("/v1/campaigns", body, Map.class);
    }
}
