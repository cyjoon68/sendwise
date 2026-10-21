package com.sendwise.carriermock.domain;

import java.util.Map;

public interface CarrierRuntime {

    CarrierSettings settings(Carrier carrier);

    SeededDraw draws(Carrier carrier);

    CarrierSettings.MergeResult update(
            Carrier carrier,
            Integer maxTps,
            Long callbackDelayMillis,
            Double callbackLossRatio,
            LatencySpec latency,
            Map<CommonCode, Double> failureRatio);
}
