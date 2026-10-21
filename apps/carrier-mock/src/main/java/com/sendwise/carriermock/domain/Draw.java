package com.sendwise.carriermock.domain;

public record Draw(long latencyMillis, CommonCode commonCode, boolean dropCallback) {
}
