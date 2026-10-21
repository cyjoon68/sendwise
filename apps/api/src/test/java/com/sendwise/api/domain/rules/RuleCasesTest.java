package com.sendwise.api.domain.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sendwise.api.infra.rules.RuleTables;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RuleCasesTest {
    private static ObjectMapper mapper;
    private static MessageRules rules;
    private static TextMeasure measure;
    private static CodeBook codes;
    private static Path dir;

    @BeforeAll
    static void loadTables() throws Exception {
        load();
    }

    static void load() throws Exception {
        if (mapper != null) {
            return;
        }
        mapper = new ObjectMapper();
        dir = RuleTables.resolve(Path.of("../../packages/rules"));
        rules = MessageRules.from(mapper.readTree(dir.resolve("message-rules.json").toFile()));
        measure = TextMeasure.from(rules, mapper.readTree(dir.resolve("ksx1001.json").toFile()));
        codes = CodeBook.from(mapper.readTree(dir.resolve("carrier-codes.json").toFile()));
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> cases() throws Exception {
        load();
        return Stream.of("ad.json", "bytes.json", "encoding.json", "carrier.json", "phone.json", "personalization.json")
                .flatMap(RuleCasesTest::rows);
    }

    private static Stream<org.junit.jupiter.params.provider.Arguments> rows(String file) {
        try {
            JsonNode root = new ObjectMapper().readTree(dir.resolve("cases").resolve(file).toFile());
            return Stream.of(root).flatMap(node -> {
                java.util.stream.Stream.Builder<org.junit.jupiter.params.provider.Arguments> builder = Stream.builder();
                node.forEach(row -> builder.add(org.junit.jupiter.params.provider.Arguments.of(
                        row.get("name").asText(),
                        file,
                        row.get("input"),
                        row.get("expected")
                )));
                return builder.build();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(file, exception);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    void matchesSharedCase(String name, String file, JsonNode input, JsonNode expected) {
        JsonNode actual = solve(file, input);
        expected.fieldNames().forEachRemaining(field ->
                assertEquals(expected.get(field), actual.get(field), name + " " + field));
    }

    private JsonNode solve(String file, JsonNode input) {
        return switch (file) {
            case "ad.json" -> ad(input);
            case "bytes.json" -> bytes(input, false);
            case "encoding.json" -> bytes(input, true);
            case "carrier.json" -> carrier(input);
            case "phone.json" -> phone(input);
            case "personalization.json" -> mapper.valueToTree(Personalization.apply(input, measure));
            default -> throw new IllegalArgumentException(file);
        };
    }

    private ObjectNode ad(JsonNode input) {
        AdPolicy.Decision decision = AdPolicy.decide(
                input.path("purpose").asText(),
                input.path("body").asText(),
                input.path("companyName").asText(),
                Instant.parse(input.path("scheduledAt").asText()),
                rules
        );
        ObjectNode node = mapper.createObjectNode();
        node.put("inBlockedHours", decision.inBlockedHours());
        if (decision.hasPrefix() != null) {
            node.put("hasPrefix", decision.hasPrefix());
        }
        if (decision.hasOptOut() != null) {
            node.put("hasOptOut", decision.hasOptOut());
        }
        if (decision.hasCompanyName() != null) {
            node.put("hasCompanyName", decision.hasCompanyName());
        }
        node.put("allowed", decision.allowed());
        return node;
    }

    private ObjectNode bytes(JsonNode input, boolean encoding) {
        TextMeasure.Result result = measure.measure(input.path("text").asText());
        ObjectNode node = mapper.createObjectNode();
        node.put("encodable", result.encodable());
        if (result.bytes() == null) {
            node.putNull("bytes");
        } else {
            node.put("bytes", result.bytes());
        }
        if (encoding) {
            node.put("inHangul2350", result.inHangul2350());
            ArrayNode chars = node.putArray("unencodable");
            result.unencodable().forEach(chars::add);
            return node;
        }
        if (result.channel() == null) {
            node.putNull("channel");
        } else {
            node.put("channel", result.channel());
        }
        node.put("overLimit", result.overLimit());
        return node;
    }

    private ObjectNode carrier(JsonNode input) {
        ObjectNode node = mapper.createObjectNode();
        String common = codes.toCommon(input.path("carrier").asText(), input.path("code").asText());
        if (common == null) {
            node.putNull("common");
        } else {
            node.put("common", common);
        }
        return node;
    }

    private ObjectNode phone(JsonNode input) {
        PhoneNumbers.Normalized normalized = PhoneNumbers.normalize(input.path("phone").asText());
        ObjectNode node = mapper.createObjectNode();
        if (normalized.normalized() == null) {
            node.putNull("normalized");
        } else {
            node.put("normalized", normalized.normalized());
        }
        node.put("valid", normalized.valid());
        return node;
    }
}
