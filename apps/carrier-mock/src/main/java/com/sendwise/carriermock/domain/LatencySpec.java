package com.sendwise.carriermock.domain;

public record LatencySpec(String distribution, long minMillis, long maxMillis) {
}
