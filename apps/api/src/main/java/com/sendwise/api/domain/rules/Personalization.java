package com.sendwise.api.domain.rules;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.LinkedHashMap;
import java.util.Map;

public final class Personalization {
    private Personalization() {
    }

    public static Map<String, Object> apply(JsonNode input, TextMeasure measure) {
        String template = input.path("template").asText("");
        Map<String, String> worst = new LinkedHashMap<>();
        Map<String, String> shortest = new LinkedHashMap<>();
        input.path("variables").fields().forEachRemaining(entry -> {
            String longest = entry.getValue().get(0).asText();
            String shortestValue = longest;
            for (JsonNode valueNode : entry.getValue()) {
                String text = valueNode.asText();
                if (width(measure.measure(text).bytes()) > width(measure.measure(longest).bytes())) {
                    longest = text;
                }
                if (width(measure.measure(text).bytes()) < width(measure.measure(shortestValue).bytes())) {
                    shortestValue = text;
                }
            }
            worst.put(entry.getKey(), longest);
            shortest.put(entry.getKey(), shortestValue);
        });
        String rendered = template;
        String shortestRendered = template;
        for (Map.Entry<String, String> entry : worst.entrySet()) {
            String token = "#{" + entry.getKey() + "}";
            rendered = rendered.replace(token, entry.getValue());
            shortestRendered = shortestRendered.replace(token, shortest.get(entry.getKey()));
        }
        TextMeasure.Result sized = measure.measure(rendered);
        TextMeasure.Result shortSized = measure.measure(shortestRendered);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("worst", worst);
        out.put("rendered", rendered);
        out.put("bytes", sized.bytes());
        out.put("channel", sized.channel());
        out.put("overLimit", sized.overLimit());
        out.put("encodable", sized.encodable());
        out.put("shortestBytes", shortSized.bytes());
        out.put("shortestChannel", shortSized.channel());
        return out;
    }

    private static int width(Integer bytes) {
        return bytes == null ? 0 : bytes;
    }
}
