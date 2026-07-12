package com.infina.cryptopricesimulator.counter;

import java.util.concurrent.atomic.AtomicLong;

public class SafeCounter implements Counter {

    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public void increment() {
        counter.incrementAndGet();
    }

    @Override
    public long count() {
        return counter.get();
    }
}
