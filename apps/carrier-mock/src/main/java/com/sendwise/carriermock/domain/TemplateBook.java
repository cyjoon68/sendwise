package com.sendwise.carriermock.domain;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateBook {

    private static final Pattern PLACEHOLDER = Pattern.compile("#\\{([^{}]+)}");

    private final Map<String, String> bodies;

    public TemplateBook(Map<String, String> bodies) {
        this.bodies = Map.copyOf(bodies);
    }

    public boolean matches(String templateCode, String body, Map<String, String> variables) {
        if (templateCode == null || templateCode.isBlank() || body == null) {
            return false;
        }
        String template = bodies.get(templateCode);
        if (template == null) {
            return false;
        }
        Map<String, String> values = variables == null ? Map.of() : variables;
        return render(template, values).equals(body);
    }

    public String render(String template, Map<String, String> variables) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value == null ? "" : value));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }
}
