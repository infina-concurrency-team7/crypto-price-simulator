package com.infina.cryptopricesimulator.dto;

import com.infina.cryptopricesimulator.model.Coin;

public record CoinStatResponse(
        Coin coin,
        long initial,
        long expected,
        long unsafe,
        long safe,
        long expectedUpdateCount,
        long unsafeUpdateCount,
        long safeUpdateCount
) {}