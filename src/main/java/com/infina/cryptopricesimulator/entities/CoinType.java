package com.infina.cryptopricesimulator.entities;


/**
 * Sistem tarafından desteklenen kripto para birimlerini
 * ve bu birimlerin başlangıç (varsayılan) fiyatlarını tutan enum yapısı.
 * Sınırlı ve sabit sayıda veriyi gruplamak için en güvenli yoldur. Tip güvenliği (type safety) sağlar
 */
public enum CoinType {
    BTC("Bitcoin", 65000L),
    ETH("Ethereum", 4500L),
    XRP("Ripple", 3L);

    private final String displayName;
    private final long initialPrice;

    CoinType(String displayName, long initialPrice) {
        this.displayName = displayName;
        this.initialPrice = initialPrice;
    }

    public String getDisplayName() {
        return displayName;
    }

    public long getInitialPrice() {
        return initialPrice;
    }
}
