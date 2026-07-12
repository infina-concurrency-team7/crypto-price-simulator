package com.infina.cryptopricesimulator.model;

public record Snapshot(Coin coin, long currentPrice, long lastDelta, long updateCount, String lastUpdatedBy) {


}
