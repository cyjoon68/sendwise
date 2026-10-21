package com.sendwise.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DebitConcurrencyTest {
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
        jdbc.update("insert into client(name) values ('debit')");
        clientId = jdbc.queryForObject("select max(id) from client", Long.class);
        jdbc.update("insert into balance(client_id, amount) values (?, ?)", clientId, 500);
        jdbc.update("insert into sender_number(client_id, phone, status, created_at) values (?, '0212345678', 'APPROVED', utc_timestamp(6))", clientId);
    }

    @Test
    void parallelDebitsNeverGoNegative() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(100);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            int n = i;
            futures.add(pool.submit(() -> post(n).getStatusCode().value()));
        }
        int ok = 0;
        int rejected = 0;
        for (Future<Integer> future : futures) {
            int status = future.get();
            if (status == 201) {
                ok++;
            } else if (status == 402) {
                rejected++;
            } else {
                throw new AssertionError("unexpected status " + status);
            }
        }
        pool.shutdown();
        long balance = jdbc.queryForObject("select amount from balance where client_id = ?", Long.class, clientId);
        assertTrue(balance >= 0);
        assertEquals(500, balance + ok * 9L);
        assertTrue(balance < 9);
        assertEquals(100, ok + rejected);
        Long negatives = jdbc.queryForObject("select count(*) from balance where amount < 0", Long.class);
        assertEquals(0L, negatives);
    }

    private ResponseEntity<Map> post(int n) {
        String phone = String.format("010%08d", 20000000 + n);
        Map<String, Object> body = Map.of(
                "clientId", clientId,
                "channel", "SMS",
                "purpose", "NORMAL",
                "body", "ping",
                "fallback", "NONE",
                "senderNumber", "0212345678",
                "recipients", List.of(phone)
        );
        return rest.postForEntity("/v1/campaigns", body, Map.class);
    }
}
