package com.sendwise.carriermock.presentation;

import com.sendwise.carriermock.domain.Carrier;
import com.sendwise.carriermock.domain.CarrierRuntime;
import com.sendwise.carriermock.domain.CarrierSettings;
import com.sendwise.carriermock.domain.CommonCode;
import com.sendwise.carriermock.domain.LatencySpec;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private final CarrierRuntime runtime;

    public AdminController(CarrierRuntime runtime) {
        this.runtime = runtime;
    }

    @PutMapping("/admin/carriers/{carrier}")
    public ResponseEntity<?> update(
            @PathVariable String carrier,
            @RequestBody CarrierSettingsBody body) {
        Carrier parsed = parseCarrier(carrier);
        if (parsed == null) {
            return ResponseEntity.badRequest().body(Map.of("reason", "UNKNOWN_CARRIER"));
        }
        LatencySpec latency = null;
        if (body.latency() != null) {
            latency = new LatencySpec(
                    body.latency().distribution(),
                    body.latency().minMillis() == null ? -1 : body.latency().minMillis(),
                    body.latency().maxMillis() == null ? -1 : body.latency().maxMillis());
        }
        Map<CommonCode, Double> ratios = null;
        if (body.failureRatio() != null) {
            ratios = new EnumMap<>(CommonCode.class);
            for (Map.Entry<String, Double> entry : body.failureRatio().entrySet()) {
                CommonCode code = parseCommon(entry.getKey());
                if (code == null) {
                    return ResponseEntity.badRequest().body(Map.of("reason", "INVALID_FAILURE_RATIO"));
                }
                ratios.put(code, entry.getValue());
            }
        }
        CarrierSettings.MergeResult merged = runtime.update(
                parsed,
                body.maxTps(),
                body.callbackDelayMillis(),
                body.callbackLossRatio(),
                latency,
                ratios);
        if (merged.error() != null) {
            return ResponseEntity.badRequest().body(Map.of("reason", merged.error()));
        }
        return ResponseEntity.ok(merged.settings());
    }

    private static Carrier parseCarrier(String raw) {
        try {
            return Carrier.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static CommonCode parseCommon(String raw) {
        try {
            return CommonCode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
