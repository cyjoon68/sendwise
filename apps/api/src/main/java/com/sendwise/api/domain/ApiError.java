package com.sendwise.api.domain;

import java.util.Map;

public final class ApiError extends RuntimeException {
    private final int status;
    private final String code;
    private final Map<String, Object> extra;

    public ApiError(int status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiError(int status, String code, String message, Map<String, Object> extra) {
        super(message);
        this.status = status;
        this.code = code;
        this.extra = extra;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, Object> extra() {
        return extra;
    }
}
