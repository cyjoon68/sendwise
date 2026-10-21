package com.sendwise.carriermock.domain;

public final class AcceptSend {

    private final TpsGate tpsGate;
    private final CarrierRuntime runtime;
    private final TemplateBook templates;
    private final CarrierCodeBook codes;
    private final ResultSink sink;

    public AcceptSend(
            TpsGate tpsGate,
            CarrierRuntime runtime,
            TemplateBook templates,
            CarrierCodeBook codes,
            ResultSink sink) {
        this.tpsGate = tpsGate;
        this.runtime = runtime;
        this.templates = templates;
        this.codes = codes;
        this.sink = sink;
    }

    public Receipt accept(SendCommand command, long nowMillis) {
        String carrierRaw = command.carrierRaw();
        if (command.messageId() == null || command.messageId().isBlank()) {
            return Receipt.rejected(command.messageId(), carrierRaw, "MISSING_MESSAGE_ID");
        }
        if (command.carrier() == null) {
            return Receipt.rejected(command.messageId(), carrierRaw, "UNKNOWN_CARRIER");
        }
        if (command.recipient() == null || command.recipient().isBlank()) {
            return Receipt.rejected(command.messageId(), carrierRaw, "MISSING_RECIPIENT");
        }
        if (command.body() == null || command.body().isBlank()) {
            return Receipt.rejected(command.messageId(), carrierRaw, "MISSING_BODY");
        }
        if (command.channel() == null) {
            return Receipt.rejected(command.messageId(), carrierRaw, "UNKNOWN_CHANNEL");
        }
        CarrierSettings settings = runtime.settings(command.carrier());
        if (!tpsGate.tryAcquire(command.carrier(), settings.maxTps(), nowMillis)) {
            return Receipt.rejected(command.messageId(), carrierRaw, "TPS_EXCEEDED");
        }
        boolean templateMismatch = command.channel() == Channel.ALIMTALK
                && !templates.matches(command.templateCode(), command.body(), command.variables());
        Draw draw = runtime.draws(command.carrier()).decide(
                settings.latency(),
                templateMismatch,
                settings.failureRatio(),
                settings.callbackLossRatio());
        if (!draw.dropCallback()) {
            CommonCode commonCode = templateMismatch ? CommonCode.ETC : draw.commonCode();
            sink.schedule(
                    settings.callbackDelayMillis() + draw.latencyMillis(),
                    new CallbackMessage(
                            command.messageId(),
                            command.carrier(),
                            command.recipient(),
                            command.channel(),
                            codes.carrierCode(command.carrier(), commonCode),
                            commonCode));
        }
        return Receipt.accepted(command.messageId(), carrierRaw);
    }
}
