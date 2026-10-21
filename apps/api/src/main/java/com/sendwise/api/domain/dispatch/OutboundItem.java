package com.sendwise.api.domain.dispatch;

import java.time.Instant;

public record OutboundItem(
        long messageId,
        String carrier,
        String channel,
        String recipient,
        String body,
        String templateCode,
        String variablesJson,
        String purpose,
        long seq,
        Instant notBefore
) {
    public static int rank(String purpose) {
        return switch (purpose) {
            case "AUTH" -> 0;
            case "NORMAL" -> 1;
            default -> 2;
        };
    }

    public OutboundItem withSeq(long next) {
        return new OutboundItem(messageId, carrier, channel, recipient, body, templateCode, variablesJson, purpose, next, notBefore);
    }

    public String queueKey() {
        return "ALIMTALK".equals(channel) ? "KAKAO" : carrier;
    }
}
