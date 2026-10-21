package com.sendwise.carriermock.presentation;

import com.sendwise.carriermock.domain.Carrier;
import com.sendwise.carriermock.domain.Channel;
import com.sendwise.carriermock.domain.SendCommand;
import java.util.Map;

public record SendBody(
        String messageId,
        String carrier,
        String recipient,
        String body,
        String channel,
        String templateCode,
        Map<String, String> variables) {

    public SendCommand toCommand() {
        return new SendCommand(
                messageId,
                carrier,
                parseCarrier(carrier),
                recipient,
                body,
                parseChannel(channel),
                templateCode,
                variables);
    }

    private static Carrier parseCarrier(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Carrier.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Channel parseChannel(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Channel.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
