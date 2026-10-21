package com.sendwise.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
class SeededTemplateMatchTest {
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        MysqlSuite.register(registry);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void approvedSeedTemplatesExistInCarrierMock() throws Exception {
        Path path = Path.of("../carrier-mock/src/main/resources/application.yml");
        Map<String, String> mock = mockTemplates(path);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "select template_code, body from alimtalk_template where client_id = 1 and status = 'APPROVED' order by id"
        );
        assertFalse(rows.isEmpty());
        for (Map<String, Object> row : rows) {
            String code = (String) row.get("template_code");
            assertEquals(row.get("body"), mock.get(code), code);
        }
    }

    private static Map<String, String> mockTemplates(Path path) throws Exception {
        Object loaded = new Yaml().load(Files.readString(path));
        Object carrier = ((Map<?, ?>) loaded).get("carrier-mock");
        Object templates = ((Map<?, ?>) carrier).get("templates");
        Map<String, String> bodies = new LinkedHashMap<>();
        for (Object item : (List<?>) templates) {
            Map<?, ?> template = (Map<?, ?>) item;
            bodies.put(String.valueOf(template.get("code")), String.valueOf(template.get("body")));
        }
        return bodies;
    }
}
