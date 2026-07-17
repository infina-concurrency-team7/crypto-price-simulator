package com.infina.cryptopricesimulator.dto;

public record SafeCoinResponse(
        String id,
        long initialPrice,
        long currentPrice,
        long updateCount,
        long lastDelta,
        String lastUpdatedBy
) {
}
