package com.sendwise.carriermock.domain;

import java.util.Map;

public record SendCommand(
        String messageId,
        String carrierRaw,
        Carrier carrier,
        String recipient,
        String body,
        Channel channel,
        String templateCode,
        Map<String, String> variables) {
}
