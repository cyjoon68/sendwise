package com.sendwise.carriermock.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SeededDrawTest {

    @Test
    void walksFailureTypesInFixedOrder() {
        Map<CommonCode, Double> ratios = Map.of(
                CommonCode.INVALID_NUMBER, 0.1,
                CommonCode.OPTED_OUT, 0.1);

        assertEquals(CommonCode.INVALID_NUMBER, SeededDraw.pick(0.0, ratios));
        assertEquals(CommonCode.OPTED_OUT, SeededDraw.pick(0.1, ratios));
        assertEquals(CommonCode.SUCCESS, SeededDraw.pick(0.2, ratios));
    }

    @Test
    void returnsSuccessWhenEveryRatioIsZero() {
        assertEquals(CommonCode.SUCCESS, SeededDraw.pick(0.99, Map.of()));
    }

    @Test
    void ratioOneAlwaysSelectsThatFailure() {
        SeededDraw draw = new SeededDraw(1);
        LatencySpec latency = new LatencySpec("fixed", 0, 0);
        Map<CommonCode, Double> ratios = Map.of(CommonCode.DEVICE_OFF, 1.0);

        for (int i = 0; i < 10; i++) {
            assertEquals(CommonCode.DEVICE_OFF, draw.decide(latency, false, ratios, 0).commonCode());
        }
    }

    @Test
    void ratioOneAlwaysDropsTheCallback() {
        SeededDraw draw = new SeededDraw(5);
        LatencySpec latency = new LatencySpec("fixed", 0, 0);

        for (int i = 0; i < 10; i++) {
            assertTrue(draw.decide(latency, false, Map.of(), 1).dropCallback());
        }
    }

    @Test
    void fixedLatencyUsesMinWithoutTheMax() {
        Draw draw = new SeededDraw(1).decide(new LatencySpec("fixed", 15, 99), false, Map.of(), 0);

        assertEquals(15, draw.latencyMillis());
        assertEquals(CommonCode.SUCCESS, draw.commonCode());
    }

    @Test
    void uniformLatencyStaysInsideTheInclusiveRange() {
        SeededDraw draw = new SeededDraw(3);
        LatencySpec latency = new LatencySpec("uniform", 10, 12);

        for (int i = 0; i < 40; i++) {
            long sample = draw.decide(latency, false, Map.of(), 0).latencyMillis();
            assertTrue(sample >= 10 && sample <= 12, Long.toString(sample));
        }
    }

    @Test
    void sameSeedRepeatsTheSameDecisions() {
        LatencySpec latency = new LatencySpec("uniform", 0, 1_000);
        Map<CommonCode, Double> ratios = Map.of(CommonCode.ETC, 0.5);
        SeededDraw left = new SeededDraw(42);
        SeededDraw right = new SeededDraw(42);

        for (int i = 0; i < 15; i++) {
            assertEquals(
                    left.decide(latency, false, ratios, 0.3),
                    right.decide(latency, false, ratios, 0.3));
        }
    }

    @Test
    void differentSeedsDiverge() {
        assertNotEquals(sequence(1), sequence(2));
    }

    private static List<Draw> sequence(long seed) {
        SeededDraw draw = new SeededDraw(seed);
        LatencySpec latency = new LatencySpec("uniform", 0, 1_000);
        Map<CommonCode, Double> ratios = Map.of(CommonCode.ETC, 0.5);
        List<Draw> draws = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            draws.add(draw.decide(latency, false, ratios, 0.3));
        }
        return draws;
    }
}
