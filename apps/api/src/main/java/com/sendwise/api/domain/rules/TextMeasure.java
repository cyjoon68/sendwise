package com.sendwise.api.domain.rules;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TextMeasure {
    private final MessageRules rules;
    private final Set<Integer> hangul;
    private final Set<Integer> nonHangul;

    public TextMeasure(MessageRules rules, Set<Integer> hangul, Set<Integer> nonHangul) {
        this.rules = rules;
        this.hangul = hangul;
        this.nonHangul = nonHangul;
    }

    public static TextMeasure from(MessageRules rules, JsonNode ksx) {
        Set<Integer> hangul = codePoints(ksx == null ? "" : ksx.path("hangul").asText(""));
        Set<Integer> nonHangul = codePoints(ksx == null ? "" : ksx.path("nonHangul").asText(""));
        return new TextMeasure(rules, hangul, nonHangul);
    }

    public record Result(
            boolean encodable,
            Integer bytes,
            String channel,
            boolean overLimit,
            boolean inHangul2350,
            List<String> unencodable
    ) {
    }

    public Result measure(String text) {
        String value = text == null ? "" : text;
        int bytes = 0;
        List<String> unencodable = new ArrayList<>();
        boolean inHangul2350 = !value.isEmpty();
        for (int i = 0; i < value.length(); ) {
            int cp = value.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            i += Character.charCount(cp);
            if (cp <= 0x7F) {
                bytes += rules.asciiBytes();
                inHangul2350 = false;
                continue;
            }
            if (hangul.contains(cp)) {
                bytes += rules.ksx1001Bytes();
                continue;
            }
            inHangul2350 = false;
            if (nonHangul.contains(cp)) {
                bytes += rules.ksx1001Bytes();
                continue;
            }
            unencodable.add(ch);
        }
        if (!unencodable.isEmpty()) {
            return new Result(false, null, null, false, false, unencodable);
        }
        boolean overLimit = bytes > rules.lmsMaxBytes();
        String channel = null;
        if (!overLimit) {
            channel = bytes <= rules.smsMaxBytes() ? "SMS" : "LMS";
        }
        return new Result(true, bytes, channel, overLimit, inHangul2350, unencodable);
    }

    private static Set<Integer> codePoints(String text) {
        Set<Integer> points = new HashSet<>();
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            points.add(cp);
            i += Character.charCount(cp);
        }
        return points;
    }
}
