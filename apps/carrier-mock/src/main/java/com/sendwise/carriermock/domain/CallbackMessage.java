package com.sendwise.carriermock.domain;

public record CallbackMessage(
        String messageId,
        Carrier carrier,
        String recipient,
        Channel channel,
        String carrierCode,
        CommonCode commonCode) {
}
