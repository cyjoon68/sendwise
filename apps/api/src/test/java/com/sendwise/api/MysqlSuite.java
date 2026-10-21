package com.sendwise.api;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;

public final class MysqlSuite {
    public static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    static {
        MYSQL.start();
    }

    private MysqlSuite() {
    }

    public static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("sendwise.dispatcher-enabled", () -> "false");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "30");
    }
}
