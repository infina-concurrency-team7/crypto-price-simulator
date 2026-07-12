package com.infina.cryptopricesimulator.state;


import com.infina.cryptopricesimulator.model.Coin;
import com.infina.cryptopricesimulator.model.Snapshot;
import lombok.Getter;


@Getter
public abstract class CoinState {

    private final Coin coin;
    private long currentPrice;
    private long lastDelta;
    private long updateCount;
    private String lastUpdatedBy;

    protected CoinState(Coin coin) {
        this.coin = coin;
        this.currentPrice = coin.getInitialPrice();
        this.lastDelta = 0;
        this.updateCount = 0;
        this.lastUpdatedBy = null;
    }

    public Snapshot snapshot() {
        return new Snapshot(coin, currentPrice, lastDelta, updateCount, lastUpdatedBy);
    }

    public void applyDelta(long delta) {
        this.currentPrice += delta;
        this.updateCount++;
        this.lastDelta = delta;
        this.lastUpdatedBy = Thread.currentThread().getName();
    }


}
