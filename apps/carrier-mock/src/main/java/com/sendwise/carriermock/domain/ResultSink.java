package com.sendwise.carriermock.domain;

public interface ResultSink {

    void schedule(long delayMillis, CallbackMessage message);
}
