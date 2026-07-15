package com.infina.cryptopricesimulator.dto;

public record CoinStatResponse(
        String coinId,
        long initial,
        long expected,
        long unsafe,
        long safe,
        long expectedUpdateCount,
        long unsafeUpdateCount,
        long safeUpdateCount
) {}