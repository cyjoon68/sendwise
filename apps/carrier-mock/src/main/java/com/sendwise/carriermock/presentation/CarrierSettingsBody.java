package com.sendwise.carriermock.presentation;

import java.util.Map;

public record CarrierSettingsBody(
        Integer maxTps,
        Long callbackDelayMillis,
        Double callbackLossRatio,
        LatencyBody latency,
        Map<String, Double> failureRatio) {

    public record LatencyBody(String distribution, Long minMillis, Long maxMillis) {
    }
}
