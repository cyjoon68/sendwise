package com.sendwise.api.domain.rules;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class CodeBook {
    private final Map<String, Map<String, String>> tables;

    public CodeBook(Map<String, Map<String, String>> tables) {
        this.tables = tables;
    }

    public static CodeBook defaults() {
        Map<String, Map<String, String>> tables = new HashMap<>();
        tables.put("SKT", Map.of(
                "0", "SUCCESS",
                "101", "INVALID_NUMBER",
                "102", "OPTED_OUT",
                "201", "DEVICE_OFF",
                "301", "SPAM_BLOCKED",
                "999", "ETC"
        ));
        tables.put("KT", Map.of(
                "OK", "SUCCESS",
                "E101", "INVALID_NUMBER",
                "E102", "OPTED_OUT",
                "E201", "DEVICE_OFF",
                "E301", "SPAM_BLOCKED",
                "E999", "ETC"
        ));
        tables.put("LGU", Map.of(
                "S", "SUCCESS",
                "N", "INVALID_NUMBER",
                "R", "OPTED_OUT",
                "D", "DEVICE_OFF",
                "P", "SPAM_BLOCKED",
                "X", "ETC"
        ));
        return new CodeBook(tables);
    }

    public static CodeBook from(JsonNode root) {
        if (root == null || !root.isObject()) {
            return defaults();
        }
        Map<String, Map<String, String>> tables = new HashMap<>();
        root.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isObject()) {
                return;
            }
            Map<String, String> codes = new HashMap<>();
            entry.getValue().fields().forEachRemaining(code -> {
                if (code.getValue().isTextual()) {
                    codes.put(code.getKey(), code.getValue().asText());
                }
            });
            tables.put(entry.getKey().toUpperCase(Locale.ROOT), codes);
        });
        return new CodeBook(tables);
    }

    public String toCommon(String carrier, String code) {
        if (carrier == null || code == null) {
            return null;
        }
        Map<String, String> table = tables.get(carrier.toUpperCase(Locale.ROOT));
        if (table == null) {
            return null;
        }
        return table.get(code);
    }
}
