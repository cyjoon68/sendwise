package com.sendwise.carriermock.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TpsGateTest {

    @Test
    void rejectsWhenTheWindowIsFull() {
        TpsGate gate = new TpsGate();

        assertTrue(gate.tryAcquire(Carrier.SKT, 2, 0));
        assertTrue(gate.tryAcquire(Carrier.SKT, 2, 10));
        assertFalse(gate.tryAcquire(Carrier.SKT, 2, 20));
    }

    @Test
    void expiresAHitAfterOneSecond() {
        TpsGate gate = new TpsGate();

        assertTrue(gate.tryAcquire(Carrier.SKT, 1, 0));
        assertFalse(gate.tryAcquire(Carrier.SKT, 1, 999));
        assertTrue(gate.tryAcquire(Carrier.SKT, 1, 1_000));
    }

    @Test
    void keepsASeparateWindowPerCarrier() {
        TpsGate gate = new TpsGate();

        assertTrue(gate.tryAcquire(Carrier.SKT, 1, 0));
        assertTrue(gate.tryAcquire(Carrier.KT, 1, 0));
        assertFalse(gate.tryAcquire(Carrier.SKT, 1, 1));
    }

    @Test
    void rejectsEverySendWhenMaxTpsIsZero() {
        assertFalse(new TpsGate().tryAcquire(Carrier.LGU, 0, 0));
    }
}
