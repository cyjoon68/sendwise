package com.sendwise.carriermock.infra;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sendwise.carriermock.domain.Carrier;
import com.sendwise.carriermock.domain.CarrierCodeBook;
import com.sendwise.carriermock.domain.CommonCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SharedCarrierCodesTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final CodesLoader loader = new CodesLoader(mapper);

    @Test
    void skipsNonCarrierKeys() {
        byte[] json = """
                {"mock":true,"EXTRA":{"Z":"ETC"},"SKT":{"0":"SUCCESS","101":"INVALID_NUMBER","102":"OPTED_OUT","201":"DEVICE_OFF","301":"SPAM_BLOCKED","999":"ETC"},"KT":{"OK":"SUCCESS","E101":"INVALID_NUMBER","E102":"OPTED_OUT","E201":"DEVICE_OFF","E301":"SPAM_BLOCKED","E999":"ETC"},"LGU":{"S":"SUCCESS","N":"INVALID_NUMBER","R":"OPTED_OUT","D":"DEVICE_OFF","P":"SPAM_BLOCKED","X":"ETC"}}
                """.getBytes();

        var parsed = loader.parse(json);

        assertFalse(parsed.containsKey("mock"));
        assertFalse(parsed.containsKey("EXTRA"));
        assertEquals("SUCCESS", parsed.get("SKT").get("0"));
    }

    @Test
    void resolvesSharedCarrierCases() throws Exception {
        CarrierCodeBook book = CarrierCodeBook.from(loader.load(null));
        JsonNode cases = mapper.readTree(rules("cases/carrier.json").toFile());

        for (JsonNode item : cases) {
            Carrier carrier = Carrier.valueOf(item.get("input").get("carrier").asText());
            String code = item.get("input").get("code").asText();
            JsonNode expected = item.get("expected").get("common");
            String name = item.get("name").asText();
            if (expected.isNull()) {
                assertNull(book.common(carrier, code), name);
                continue;
            }
            CommonCode common = CommonCode.valueOf(expected.asText());
            assertEquals(common, book.common(carrier, code), name);
            assertEquals(code, book.carrierCode(carrier, common), name);
        }
    }

    private static Path rules(String relative) {
        Path dir = Path.of("").toAbsolutePath();
        for (int i = 0; i < 4 && dir != null; i++) {
            Path candidate = dir.resolve("packages/rules").resolve(relative);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException("missing packages/rules/" + relative);
    }
}
