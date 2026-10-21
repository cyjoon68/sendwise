package com.sendwise.carriermock.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateBookTest {

    private final TemplateBook book = new TemplateBook(Map.of("AUTH", "인증번호는 #{code}입니다."));

    @Test
    void substitutesPlaceholders() {
        assertTrue(book.matches("AUTH", "인증번호는 1234입니다.", Map.of("code", "1234")));
    }

    @Test
    void rejectsADifferentBody() {
        assertFalse(book.matches("AUTH", "인증번호는 9999입니다.", Map.of("code", "1234")));
    }

    @Test
    void missingVariableBecomesEmpty() {
        assertEquals("인증번호는 입니다.", book.render("인증번호는 #{code}입니다.", Map.of()));
        assertTrue(book.matches("AUTH", "인증번호는 입니다.", Map.of()));
    }

    @Test
    void unknownTemplateDoesNotMatch() {
        assertFalse(book.matches("NOPE", "인증번호는 1234입니다.", Map.of("code", "1234")));
    }
}
