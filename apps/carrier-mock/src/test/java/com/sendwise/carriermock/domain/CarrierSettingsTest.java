package com.sendwise.carriermock.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CarrierSettingsTest {

    @Test
    void rejectsFailureRatiosThatSumPastOne() {
        CarrierSettings settings = open();

        CarrierSettings.MergeResult merged = settings.merge(
                null,
                null,
                null,
                null,
                Map.of(CommonCode.ETC, 0.6, CommonCode.INVALID_NUMBER, 0.6));

        assertEquals("INVALID_FAILURE_RATIO", merged.error());
        assertNull(merged.settings());
        assertEquals(0, settings.maxTps());
    }

    @Test
    void keepsOmittedFields() {
        CarrierSettings settings = new CarrierSettings(4, 25, 0.2, new LatencySpec("fixed", 3, 3), Map.of());

        CarrierSettings.MergeResult merged = settings.merge(9, null, null, null, null);

        assertNull(merged.error());
        assertEquals(9, merged.settings().maxTps());
        assertEquals(25, merged.settings().callbackDelayMillis());
        assertEquals(0.2, merged.settings().callbackLossRatio());
        assertEquals(3, merged.settings().latency().minMillis());
    }

    private static CarrierSettings open() {
        return new CarrierSettings(0, 0, 0, new LatencySpec("fixed", 0, 0), Map.of());
    }
}
