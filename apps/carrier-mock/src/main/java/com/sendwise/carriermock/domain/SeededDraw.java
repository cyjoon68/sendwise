package com.sendwise.carriermock.domain;

import java.util.List;
import java.util.Map;
import java.util.Random;

public final class SeededDraw {

    public static final List<CommonCode> FAILURE_ORDER = List.of(
            CommonCode.INVALID_NUMBER,
            CommonCode.OPTED_OUT,
            CommonCode.DEVICE_OFF,
            CommonCode.SPAM_BLOCKED,
            CommonCode.ETC);

    private final Random random;

    public SeededDraw(long seed) {
        this.random = new Random(seed);
    }

    public synchronized Draw decide(
            LatencySpec latency,
            boolean templateMismatch,
            Map<CommonCode, Double> failureRatio,
            double callbackLossRatio) {
        long latencyMillis = sampleLatency(latency);
        CommonCode commonCode = templateMismatch ? null : pick(random.nextDouble(), failureRatio);
        boolean dropCallback = random.nextDouble() < callbackLossRatio;
        return new Draw(latencyMillis, commonCode, dropCallback);
    }

    public static CommonCode pick(double roll, Map<CommonCode, Double> failureRatio) {
        double cursor = 0;
        for (CommonCode code : FAILURE_ORDER) {
            cursor += failureRatio.getOrDefault(code, 0.0);
            if (roll < cursor) {
                return code;
            }
        }
        return CommonCode.SUCCESS;
    }

    private long sampleLatency(LatencySpec latency) {
        if (!"uniform".equals(latency.distribution())) {
            return latency.minMillis();
        }
        double roll = random.nextDouble();
        if (latency.maxMillis() <= latency.minMillis()) {
            return latency.minMillis();
        }
        long span = latency.maxMillis() - latency.minMillis();
        return latency.minMillis() + (long) Math.floor(roll * (span + 1));
    }
}
