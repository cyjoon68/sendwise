package com.sendwise.api.infra.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sendwise.api.domain.rules.CodeBook;
import com.sendwise.api.domain.rules.MessageRules;
import com.sendwise.api.domain.rules.TextMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class RuleTables {
    private static final Logger log = LoggerFactory.getLogger(RuleTables.class);
    private final ObjectMapper mapper;
    private final Path dir;

    public RuleTables(ObjectMapper mapper, @Value("${sendwise.rules-dir:../../packages/rules}") String rulesDir) {
        this.mapper = mapper;
        this.dir = resolve(Path.of(rulesDir));
    }

    public MessageRules rules() {
        return MessageRules.from(rulesJson());
    }

    public JsonNode rulesJson() {
        ObjectNode root = readObject("message-rules.json");
        MessageRules base = MessageRules.defaults();
        object(root, "sms").put("maxBytes", root.path("sms").path("maxBytes").asInt(base.smsMaxBytes()));
        object(root, "lms").put("maxBytes", root.path("lms").path("maxBytes").asInt(base.lmsMaxBytes()));
        ObjectNode ad = object(root, "ad");
        ObjectNode hours = object(ad, "blockedHours");
        if (!hours.hasNonNull("from")) {
            hours.put("from", base.blockedFrom().toString());
        }
        if (!hours.hasNonNull("to")) {
            hours.put("to", base.blockedTo().toString());
        }
        if (!hours.has("fromInclusive")) {
            hours.put("fromInclusive", base.fromInclusive());
        }
        if (!hours.has("toInclusive")) {
            hours.put("toInclusive", base.toInclusive());
        }
        if (!hours.hasNonNull("zone")) {
            hours.put("zone", base.zone().getId());
        }
        if (!ad.hasNonNull("requiredPrefix")) {
            ad.put("requiredPrefix", base.requiredPrefix());
        }
        if (!ad.has("companyNameRequired")) {
            ad.put("companyNameRequired", base.companyNameRequired());
        }
        if (!ad.hasNonNull("optOutText")) {
            ad.put("optOutText", base.optOutText());
        }
        ObjectNode price = object(root, "unitPrice");
        if (!price.has("SMS")) {
            price.put("SMS", base.smsPrice());
        }
        if (!price.has("LMS")) {
            price.put("LMS", base.lmsPrice());
        }
        if (!price.has("ALIMTALK")) {
            price.put("ALIMTALK", base.alimtalkPrice());
        }
        ObjectNode encoding = object(root, "encoding");
        if (!encoding.hasNonNull("name")) {
            encoding.put("name", "EUC-KR");
        }
        if (!encoding.has("asciiBytes")) {
            encoding.put("asciiBytes", base.asciiBytes());
        }
        if (!encoding.has("ksx1001Bytes")) {
            encoding.put("ksx1001Bytes", base.ksx1001Bytes());
        }
        return root;
    }

    public CodeBook codeBook() {
        JsonNode root = readQuiet("carrier-codes.json");
        if (root == null || !root.isObject()) {
            return CodeBook.defaults();
        }
        return CodeBook.from(root);
    }

    public TextMeasure measure() {
        return TextMeasure.from(rules(), readQuiet("ksx1001.json"));
    }

    public static Path resolve(Path configured) {
        if (configured.isAbsolute() && Files.isDirectory(configured)) {
            return configured;
        }
        Path cwd = Path.of("").toAbsolutePath();
        Path direct = cwd.resolve(configured).normalize();
        if (Files.isDirectory(direct)) {
            return direct;
        }
        Path cursor = cwd;
        while (cursor != null) {
            Path candidate = cursor.resolve("packages/rules");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            cursor = cursor.getParent();
        }
        return direct;
    }

    private ObjectNode readObject(String name) {
        JsonNode node = readQuiet(name);
        if (node instanceof ObjectNode object) {
            return object;
        }
        return mapper.createObjectNode();
    }

    private JsonNode readQuiet(String name) {
        Path file = dir.resolve(name);
        if (!Files.isRegularFile(file)) {
            log.warn("rules file missing: {}", file);
            return null;
        }
        try {
            return mapper.readTree(file.toFile());
        } catch (IOException exception) {
            log.warn("rules file unreadable: {} {}", file, exception.toString());
            return null;
        }
    }

    private ObjectNode object(ObjectNode parent, String field) {
        JsonNode child = parent.get(field);
        if (child instanceof ObjectNode object) {
            return object;
        }
        ObjectNode created = mapper.createObjectNode();
        parent.set(field, created);
        return created;
    }
}
