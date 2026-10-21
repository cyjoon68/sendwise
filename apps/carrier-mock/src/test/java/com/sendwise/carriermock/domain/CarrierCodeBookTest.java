package com.sendwise.carriermock.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CarrierCodeBookTest {

    @Test
    void mapsACommonCodeToTheCarrierCode() {
        CarrierCodeBook book = CarrierCodeBook.from(raw("SKT_SUCCESS", "KT_SUCCESS", "LGU_SUCCESS"));

        assertEquals("SKT_SUCCESS", book.carrierCode(Carrier.SKT, CommonCode.SUCCESS));
        assertEquals("KT_SUCCESS", book.carrierCode(Carrier.KT, CommonCode.SUCCESS));
        assertEquals("LGU_ETC", book.carrierCode(Carrier.LGU, CommonCode.ETC));
    }

    @Test
    void keepsTheFirstCarrierCodeWhenACommonCodeRepeats() {
        Map<String, String> skt = codes("SKT");
        skt.remove("SKT_SUCCESS");
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("FIRST", "SUCCESS");
        ordered.put("SECOND", "SUCCESS");
        ordered.putAll(skt);

        Map<String, Map<String, String>> raw = raw("SKT_SUCCESS", "KT_SUCCESS", "LGU_SUCCESS");
        raw.put("SKT", ordered);

        assertEquals("FIRST", CarrierCodeBook.from(raw).carrierCode(Carrier.SKT, CommonCode.SUCCESS));
    }

    @Test
    void unknownCarrierCodeHasNoCommonCode() {
        CarrierCodeBook book = CarrierCodeBook.from(raw("SKT_SUCCESS", "KT_SUCCESS", "LGU_SUCCESS"));

        assertEquals(null, book.common(Carrier.SKT, "12345"));
    }

    @Test
    void rejectsAnUnknownCommonCode() {
        Map<String, Map<String, String>> raw = raw("SKT_SUCCESS", "KT_SUCCESS", "LGU_SUCCESS");
        raw.get("SKT").put("SKT_OTHER", "NOT_A_CODE");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> CarrierCodeBook.from(raw));

        assertEquals("UNKNOWN_COMMON_CODE_NOT_A_CODE", error.getMessage());
    }

    private static Map<String, Map<String, String>> raw(String sktSuccess, String ktSuccess, String lguSuccess) {
        Map<String, Map<String, String>> raw = new LinkedHashMap<>();
        Map<String, String> skt = codes("SKT");
        skt.put("SKT_SUCCESS", "SUCCESS");
        skt.remove(sktSuccess);
        skt.put(sktSuccess, "SUCCESS");
        Map<String, String> kt = codes("KT");
        kt.put(ktSuccess, "SUCCESS");
        Map<String, String> lgu = codes("LGU");
        lgu.put(lguSuccess, "SUCCESS");
        raw.put("SKT", skt);
        raw.put("KT", kt);
        raw.put("LGU", lgu);
        return raw;
    }

    private static Map<String, String> codes(String prefix) {
        Map<String, String> codes = new LinkedHashMap<>();
        codes.put(prefix + "_SUCCESS", "SUCCESS");
        codes.put(prefix + "_INVALID_NUMBER", "INVALID_NUMBER");
        codes.put(prefix + "_OPTED_OUT", "OPTED_OUT");
        codes.put(prefix + "_DEVICE_OFF", "DEVICE_OFF");
        codes.put(prefix + "_SPAM_BLOCKED", "SPAM_BLOCKED");
        codes.put(prefix + "_ETC", "ETC");
        return codes;
    }
}
