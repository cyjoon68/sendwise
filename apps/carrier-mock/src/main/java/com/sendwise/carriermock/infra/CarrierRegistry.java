package com.sendwise.carriermock.infra;

import com.sendwise.carriermock.domain.Carrier;
import com.sendwise.carriermock.domain.CarrierRuntime;
import com.sendwise.carriermock.domain.CarrierSettings;
import com.sendwise.carriermock.domain.CommonCode;
import com.sendwise.carriermock.domain.LatencySpec;
import com.sendwise.carriermock.domain.SeededDraw;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public final class CarrierRegistry implements CarrierRuntime {

    private final Map<Carrier, AtomicReference<CarrierSettings>> settings = new EnumMap<>(Carrier.class);
    private final Map<Carrier, SeededDraw> draws = new EnumMap<>(Carrier.class);

    public CarrierRegistry(Map<Carrier, CarrierSettings> initial, long seed) {
        for (Carrier carrier : Carrier.values()) {
            CarrierSettings carrierSettings = initial.get(carrier);
            if (carrierSettings == null) {
                throw new IllegalArgumentException("MISSING_CARRIER_" + carrier.name());
            }
            settings.put(carrier, new AtomicReference<>(carrierSettings));
            draws.put(carrier, new SeededDraw(seed));
        }
    }

    public static CarrierRegistry from(MockProperties properties) {
        Map<Carrier, CarrierSettings> initial = new EnumMap<>(Carrier.class);
        for (Carrier carrier : Carrier.values()) {
            MockProperties.CarrierProps props = properties.getCarriers().get(carrier.name());
            if (props == null) {
                throw new IllegalArgumentException("MISSING_CARRIER_" + carrier.name());
            }
            initial.put(carrier, toSettings(props));
        }
        return new CarrierRegistry(initial, properties.getRandomSeed());
    }

    @Override
    public CarrierSettings settings(Carrier carrier) {
        return settings.get(carrier).get();
    }

    @Override
    public SeededDraw draws(Carrier carrier) {
        return draws.get(carrier);
    }

    @Override
    public CarrierSettings.MergeResult update(
            Carrier carrier,
            Integer maxTps,
            Long callbackDelayMillis,
            Double callbackLossRatio,
            LatencySpec latency,
            Map<CommonCode, Double> failureRatio) {
        AtomicReference<CarrierSettings> ref = settings.get(carrier);
        while (true) {
            CarrierSettings current = ref.get();
            CarrierSettings.MergeResult merged = current.merge(
                    maxTps,
                    callbackDelayMillis,
                    callbackLossRatio,
                    latency,
                    failureRatio);
            if (merged.error() != null || ref.compareAndSet(current, merged.settings())) {
                return merged;
            }
        }
    }

    private static CarrierSettings toSettings(MockProperties.CarrierProps props) {
        MockProperties.LatencyProps latency = props.getLatency();
        Map<CommonCode, Double> ratios = new EnumMap<>(CommonCode.class);
        for (Map.Entry<String, Double> entry : props.getFailureRatio().entrySet()) {
            ratios.put(CommonCode.valueOf(entry.getKey()), entry.getValue());
        }
        return new CarrierSettings(
                props.getMaxTps(),
                props.getCallbackDelayMillis(),
                props.getCallbackLossRatio(),
                new LatencySpec(latency.getDistribution(), latency.getMinMillis(), latency.getMaxMillis()),
                ratios);
    }
}
