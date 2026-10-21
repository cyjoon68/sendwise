package com.sendwise.api.presentation;

import com.sendwise.api.domain.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(ApiError.class)
    public ResponseEntity<Map<String, Object>> api(ApiError error) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error.code());
        body.put("message", error.getMessage());
        error.extra().forEach(body::put);
        return ResponseEntity.status(error.status()).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> unreadable() {
        return ResponseEntity.badRequest().body(Map.of("error", "VALIDATION", "message", "요청 값을 확인해 주세요"));
    }
}
