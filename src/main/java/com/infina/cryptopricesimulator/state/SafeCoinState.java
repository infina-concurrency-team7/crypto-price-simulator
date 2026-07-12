package com.infina.cryptopricesimulator.state;


import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.model.Snapshot;

import java.util.concurrent.locks.ReentrantLock;

public class SafeCoinState extends CoinState {

    private final ReentrantLock lock = new ReentrantLock();

    public SafeCoinState(Coin coin) {
        super(coin);
    }


    @Override
    public Snapshot snapshot() {
        lock.lock();
        try {
            return super.snapshot();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void applyDelta(long delta) {
        lock.lock();
        try {
            super.applyDelta(delta);
        } finally {
            lock.unlock();
        }
    }
}
