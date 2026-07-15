package com.infina.cryptopricesimulator.dto;

import java.util.List;

public record SimulationResultResponse(
        long seed,
        long submittedUpdates,
        long unsafeProcessedUpdates,
        long safeProcessedUpdates,
        int workers,
        long unsafeElapsedMs,
        long safeElapsedMs,
        long unsafeThroughputPerSec,
        long safeThroughputPerSec,
        boolean safeInvariantPassed,
        List<CoinStatResponse> coins
) {}