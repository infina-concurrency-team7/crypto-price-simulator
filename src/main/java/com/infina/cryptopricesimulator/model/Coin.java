package com.infina.cryptopricesimulator.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Coin {

    BTC("Bitcoin", "BTC", 60000),
    ETH("Ethereum", "ETH", 4000),
    SOL("Solana", "SOL", 150);

    private final String displayName;
    private final String symbol;
    private final long initialPrice;

}
