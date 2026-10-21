package com.sendwise.carriermock.domain;

public record Receipt(String messageId, String carrier, boolean accepted, String reason) {

    public static Receipt accepted(String messageId, String carrier) {
        return new Receipt(messageId, carrier, true, null);
    }

    public static Receipt rejected(String messageId, String carrier, String reason) {
        return new Receipt(messageId, carrier, false, reason);
    }
}
