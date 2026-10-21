package com.sendwise.carriermock.domain;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CarrierCodeBook {

    private final Map<Carrier, Map<CommonCode, String>> byCommon;
    private final Map<Carrier, Map<String, CommonCode>> byCarrierCode;

    private CarrierCodeBook(
            Map<Carrier, Map<CommonCode, String>> byCommon,
            Map<Carrier, Map<String, CommonCode>> byCarrierCode) {
        this.byCommon = byCommon;
        this.byCarrierCode = byCarrierCode;
    }

    public String carrierCode(Carrier carrier, CommonCode commonCode) {
        return byCommon.get(carrier).get(commonCode);
    }

    public CommonCode common(Carrier carrier, String carrierCode) {
        return byCarrierCode.get(carrier).get(carrierCode);
    }

    public static CarrierCodeBook from(Map<String, Map<String, String>> raw) {
        Map<Carrier, Map<CommonCode, String>> byCommon = new EnumMap<>(Carrier.class);
        Map<Carrier, Map<String, CommonCode>> byCarrierCode = new EnumMap<>(Carrier.class);
        for (Carrier carrier : Carrier.values()) {
            Map<String, String> carrierCodes = raw.get(carrier.name());
            if (carrierCodes == null) {
                throw new IllegalArgumentException("MISSING_CARRIER_CODES_" + carrier.name());
            }
            Map<CommonCode, String> reverse = new EnumMap<>(CommonCode.class);
            Map<String, CommonCode> forward = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : carrierCodes.entrySet()) {
                CommonCode commonCode = parseCommon(entry.getValue());
                if (commonCode == null) {
                    throw new IllegalArgumentException("UNKNOWN_COMMON_CODE_" + entry.getValue());
                }
                forward.put(entry.getKey(), commonCode);
                reverse.putIfAbsent(commonCode, entry.getKey());
            }
            for (CommonCode commonCode : CommonCode.values()) {
                if (!reverse.containsKey(commonCode)) {
                    throw new IllegalArgumentException(
                            "MISSING_COMMON_CODE_" + carrier.name() + "_" + commonCode.name());
                }
            }
            byCommon.put(carrier, Map.copyOf(reverse));
            byCarrierCode.put(carrier, Map.copyOf(forward));
        }
        return new CarrierCodeBook(Map.copyOf(byCommon), Map.copyOf(byCarrierCode));
    }

    private static CommonCode parseCommon(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return CommonCode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
