package com.sendwise.carriermock.domain;

import java.util.EnumMap;
import java.util.Map;

public record CarrierSettings(
        int maxTps,
        long callbackDelayMillis,
        double callbackLossRatio,
        LatencySpec latency,
        Map<CommonCode, Double> failureRatio) {

    public CarrierSettings {
        String error = validate(maxTps, callbackDelayMillis, callbackLossRatio, latency, failureRatio);
        if (error != null) {
            throw new IllegalArgumentException(error);
        }
        failureRatio = fill(failureRatio == null ? Map.of() : failureRatio);
    }

    public MergeResult merge(
            Integer maxTps,
            Long callbackDelayMillis,
            Double callbackLossRatio,
            LatencySpec latency,
            Map<CommonCode, Double> failureRatio) {
        int nextMax = maxTps == null ? this.maxTps : maxTps;
        long nextDelay = callbackDelayMillis == null ? this.callbackDelayMillis : callbackDelayMillis;
        double nextLoss = callbackLossRatio == null ? this.callbackLossRatio : callbackLossRatio;
        LatencySpec nextLatency = latency == null ? this.latency : latency;
        Map<CommonCode, Double> nextRatios = failureRatio == null ? this.failureRatio : failureRatio;
        String error = validate(nextMax, nextDelay, nextLoss, nextLatency, nextRatios);
        if (error != null) {
            return new MergeResult(null, error);
        }
        return new MergeResult(
                new CarrierSettings(nextMax, nextDelay, nextLoss, nextLatency, nextRatios),
                null);
    }

    public static String validate(
            int maxTps,
            long callbackDelayMillis,
            double callbackLossRatio,
            LatencySpec latency,
            Map<CommonCode, Double> failureRatio) {
        if (maxTps < 0) {
            return "INVALID_MAX_TPS";
        }
        if (callbackDelayMillis < 0) {
            return "INVALID_CALLBACK_DELAY";
        }
        if (Double.isNaN(callbackLossRatio) || callbackLossRatio < 0 || callbackLossRatio > 1) {
            return "INVALID_CALLBACK_LOSS_RATIO";
        }
        if (latency == null || latency.distribution() == null) {
            return "INVALID_LATENCY";
        }
        if (!"fixed".equals(latency.distribution()) && !"uniform".equals(latency.distribution())) {
            return "INVALID_LATENCY";
        }
        if (latency.minMillis() < 0 || latency.maxMillis() < latency.minMillis()) {
            return "INVALID_LATENCY";
        }
        Map<CommonCode, Double> ratios = failureRatio == null ? Map.of() : failureRatio;
        double sum = 0;
        for (Map.Entry<CommonCode, Double> entry : ratios.entrySet()) {
            if (!SeededDraw.FAILURE_ORDER.contains(entry.getKey()) || entry.getValue() == null) {
                return "INVALID_FAILURE_RATIO";
            }
            double value = entry.getValue();
            if (Double.isNaN(value) || value < 0 || value > 1) {
                return "INVALID_FAILURE_RATIO";
            }
            sum += value;
        }
        if (sum - 1.0d > 1e-9) {
            return "INVALID_FAILURE_RATIO";
        }
        return null;
    }

    private static Map<CommonCode, Double> fill(Map<CommonCode, Double> ratios) {
        Map<CommonCode, Double> filled = new EnumMap<>(CommonCode.class);
        for (CommonCode code : SeededDraw.FAILURE_ORDER) {
            filled.put(code, ratios.getOrDefault(code, 0.0));
        }
        return Map.copyOf(filled);
    }

    public record MergeResult(CarrierSettings settings, String error) {
    }
}
