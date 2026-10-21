package com.sendwise.carriermock.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AcceptSendTest {

    private final CarrierCodeBook codes = CarrierCodeBook.from(codeMap());
    private final TemplateBook templates = new TemplateBook(Map.of("AUTH", "인증번호는 #{code}입니다."));

    @Test
    void schedulesTheCarrierSuccessCode() {
        RecordingSink sink = new RecordingSink();
        AcceptSend acceptSend = acceptSend(settings(10, 6, 0, Map.of()), sink, 1);

        Receipt receipt = acceptSend.accept(sms("m-1", "SKT"), 0);

        assertTrue(receipt.accepted());
        assertNull(receipt.reason());
        assertEquals(1, sink.messages.size());
        assertEquals(10, sink.delays.get(0));
        assertEquals("SKT_SUCCESS", sink.messages.get(0).carrierCode());
        assertEquals(CommonCode.SUCCESS, sink.messages.get(0).commonCode());
    }

    @Test
    void secondSendInsideTheWindowIsRejectedAndNotScheduled() {
        RecordingSink sink = new RecordingSink();
        AcceptSend acceptSend = acceptSend(settings(1, 0, 0, Map.of()), sink, 1);

        Receipt first = acceptSend.accept(sms("m-1", "KT"), 50);
        Receipt second = acceptSend.accept(sms("m-2", "KT"), 60);

        assertTrue(first.accepted());
        assertFalse(second.accepted());
        assertEquals("TPS_EXCEEDED", second.reason());
        assertEquals(1, sink.messages.size());
    }

    @Test
    void blankMessageIdDoesNotConsumeTps() {
        RecordingSink sink = new RecordingSink();
        AcceptSend acceptSend = acceptSend(settings(1, 0, 0, Map.of()), sink, 1);

        Receipt blank = acceptSend.accept(sms("  ", "LGU"), 0);
        Receipt accepted = acceptSend.accept(sms("m-1", "LGU"), 0);

        assertEquals("MISSING_MESSAGE_ID", blank.reason());
        assertTrue(accepted.accepted());
        assertEquals(1, sink.messages.size());
    }

    @Test
    void templateMismatchIgnoresTheFailureRatio() {
        RecordingSink sink = new RecordingSink();
        AcceptSend acceptSend = acceptSend(
                settings(10, 0, 0, Map.of(CommonCode.INVALID_NUMBER, 1.0)),
                sink,
                1);
        SendCommand command = new SendCommand(
                "m-1",
                "SKT",
                Carrier.SKT,
                "01012345678",
                "다른 본문",
                Channel.ALIMTALK,
                "AUTH",
                Map.of("code", "1234"));

        Receipt receipt = acceptSend.accept(command, 0);

        assertTrue(receipt.accepted());
        assertEquals(CommonCode.ETC, sink.messages.get(0).commonCode());
        assertEquals("SKT_ETC", sink.messages.get(0).carrierCode());
    }

    @Test
    void matchingTemplateUsesTheFailureRatio() {
        RecordingSink sink = new RecordingSink();
        AcceptSend acceptSend = acceptSend(
                settings(10, 0, 0, Map.of(CommonCode.ETC, 1.0)),
                sink,
                1);
        SendCommand command = new SendCommand(
                "m-1",
                "KT",
                Carrier.KT,
                "01012345678",
                "인증번호는 1234입니다.",
                Channel.ALIMTALK,
                "AUTH",
                Map.of("code", "1234"));

        acceptSend.accept(command, 0);

        assertEquals(CommonCode.ETC, sink.messages.get(0).commonCode());
        assertEquals("KT_ETC", sink.messages.get(0).carrierCode());
    }

    @Test
    void smsIgnoresTheTemplateCode() {
        RecordingSink sink = new RecordingSink();
        AcceptSend acceptSend = acceptSend(settings(10, 0, 0, Map.of()), sink, 1);
        SendCommand command = new SendCommand(
                "m-1",
                "LGU",
                Carrier.LGU,
                "01012345678",
                "hello",
                Channel.SMS,
                "NOPE",
                Map.of());

        acceptSend.accept(command, 0);

        assertEquals(CommonCode.SUCCESS, sink.messages.get(0).commonCode());
    }

    @Test
    void callbackLossDoesNotSchedule() {
        RecordingSink sink = new RecordingSink();
        AcceptSend acceptSend = acceptSend(settings(10, 0, 1, Map.of()), sink, 1);

        Receipt receipt = acceptSend.accept(sms("m-1", "SKT"), 0);

        assertTrue(receipt.accepted());
        assertTrue(sink.messages.isEmpty());
    }

    private AcceptSend acceptSend(CarrierSettings settings, RecordingSink sink, long seed) {
        return new AcceptSend(new TpsGate(), new FixedRuntime(settings, seed), templates, codes, sink);
    }

    private static CarrierSettings settings(int maxTps, long delay, double loss, Map<CommonCode, Double> ratios) {
        return new CarrierSettings(maxTps, delay, loss, new LatencySpec("fixed", 4, 9), ratios);
    }

    private static SendCommand sms(String messageId, String carrier) {
        return new SendCommand(
                messageId,
                carrier,
                Carrier.valueOf(carrier),
                "01012345678",
                "hello",
                Channel.SMS,
                null,
                null);
    }

    private static Map<String, Map<String, String>> codeMap() {
        Map<String, Map<String, String>> raw = new LinkedHashMap<>();
        for (String carrier : List.of("SKT", "KT", "LGU")) {
            Map<String, String> codes = new LinkedHashMap<>();
            codes.put(carrier + "_SUCCESS", "SUCCESS");
            codes.put(carrier + "_INVALID_NUMBER", "INVALID_NUMBER");
            codes.put(carrier + "_OPTED_OUT", "OPTED_OUT");
            codes.put(carrier + "_DEVICE_OFF", "DEVICE_OFF");
            codes.put(carrier + "_SPAM_BLOCKED", "SPAM_BLOCKED");
            codes.put(carrier + "_ETC", "ETC");
            raw.put(carrier, codes);
        }
        return raw;
    }

    private static final class FixedRuntime implements CarrierRuntime {

        private final Map<Carrier, CarrierSettings> settings = new EnumMap<>(Carrier.class);
        private final Map<Carrier, SeededDraw> draws = new EnumMap<>(Carrier.class);

        private FixedRuntime(CarrierSettings settings, long seed) {
            for (Carrier carrier : Carrier.values()) {
                this.settings.put(carrier, settings);
                this.draws.put(carrier, new SeededDraw(seed));
            }
        }

        @Override
        public CarrierSettings settings(Carrier carrier) {
            return settings.get(carrier);
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
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingSink implements ResultSink {

        private final List<Long> delays = new ArrayList<>();
        private final List<CallbackMessage> messages = new ArrayList<>();

        @Override
        public void schedule(long delayMillis, CallbackMessage message) {
            delays.add(delayMillis);
            messages.add(message);
        }
    }
}
