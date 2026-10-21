package com.sendwise.api.domain.rules;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalTime;
import java.time.ZoneId;

public record MessageRules(
        int smsMaxBytes,
        int lmsMaxBytes,
        LocalTime blockedFrom,
        LocalTime blockedTo,
        boolean fromInclusive,
        boolean toInclusive,
        ZoneId zone,
        String requiredPrefix,
        boolean companyNameRequired,
        String optOutText,
        long smsPrice,
        long lmsPrice,
        long alimtalkPrice,
        int asciiBytes,
        int ksx1001Bytes
) {
    public static MessageRules defaults() {
        return new MessageRules(
                90,
                2000,
                LocalTime.of(21, 0),
                LocalTime.of(8, 0),
                true,
                false,
                ZoneId.of("Asia/Seoul"),
                "(광고)",
                true,
                "무료수신거부 080-000-0000",
                9,
                30,
                7,
                1,
                2
        );
    }

    public static MessageRules from(JsonNode root) {
        MessageRules base = defaults();
        JsonNode sms = root.path("sms");
        JsonNode lms = root.path("lms");
        JsonNode ad = root.path("ad");
        JsonNode hours = ad.path("blockedHours");
        JsonNode price = root.path("unitPrice");
        JsonNode encoding = root.path("encoding");
        return new MessageRules(
                sms.path("maxBytes").asInt(base.smsMaxBytes()),
                lms.path("maxBytes").asInt(base.lmsMaxBytes()),
                clock(hours.path("from").asText(base.blockedFrom().toString()), base.blockedFrom()),
                clock(hours.path("to").asText(base.blockedTo().toString()), base.blockedTo()),
                hours.path("fromInclusive").asBoolean(base.fromInclusive()),
                hours.path("toInclusive").asBoolean(base.toInclusive()),
                ZoneId.of(hours.path("zone").asText(base.zone().getId())),
                ad.path("requiredPrefix").asText(base.requiredPrefix()),
                ad.path("companyNameRequired").asBoolean(base.companyNameRequired()),
                ad.path("optOutText").asText(base.optOutText()),
                price.path("SMS").asLong(base.smsPrice()),
                price.path("LMS").asLong(base.lmsPrice()),
                price.path("ALIMTALK").asLong(base.alimtalkPrice()),
                encoding.path("asciiBytes").asInt(base.asciiBytes()),
                encoding.path("ksx1001Bytes").asInt(base.ksx1001Bytes())
        );
    }

    private static LocalTime clock(String value, LocalTime fallback) {
        if (value == null || value.isBlank() || "null".equals(value)) {
            return fallback;
        }
        String[] parts = value.split(":");
        return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
    }

    public long price(String channel) {
        return switch (channel) {
            case "SMS" -> smsPrice;
            case "LMS" -> lmsPrice;
            case "ALIMTALK" -> alimtalkPrice;
            default -> throw new IllegalArgumentException(channel);
        };
    }
}
