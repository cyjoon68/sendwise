package com.sendwise.api.domain.rules;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateRender {
    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{([^{}]+)}");

    private TemplateRender() {
    }

    public static String render(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        Map<String, String> values = variables == null ? Map.of() : variables;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
