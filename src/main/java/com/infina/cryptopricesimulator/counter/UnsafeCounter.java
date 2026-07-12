package com.infina.cryptopricesimulator.counter;

public class UnsafeCounter implements Counter {

    private long count = 0;

    @Override
    public void increment() {
        count++;
    }

    @Override
    public long count() {
        return count;
    }
}
