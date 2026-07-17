package com.infina.cryptopricesimulator.api;

import com.infina.cryptopricesimulator.dto.CoinStatResponse;
import com.infina.cryptopricesimulator.dto.SafeCoinResponse;
import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SIMULATION STATE HOLDER
 *
 * Nedir?
 * Programın içindeki geçici bir not defteridir (hafıza kutusudur).
 *
 * Neden Kullanılır?
 * 1. Sonuçları Kaybetmemek İçin: Simülasyon bittikten sonra çıkan tüm verileri
 *    burada saklarız ki uçup gitmesinler.
 * 2. İstenildiğinde Gösterebilmek İçin: Kullanıcı sonradan son simülasyonun
 *    istatistiklerini veya güncel coin fiyatlarını görmek istediğinde, bu not
 *    defterini açıp bilgileri anında sunabilmek için kullanılır.
 */

@Component
public class SimulationStateHolder {

    private final AtomicReference<SimulationResultResponse> lastStats = new AtomicReference<>(null);
    private final List<SafeCoinResponse> lastSafeCoins = new CopyOnWriteArrayList<>();

    public void updateResults(SimulationResultResponse stats, List<SafeCoinResponse> safeCoins) {
        this.lastStats.set(stats);
        this.lastSafeCoins.clear();

        if (stats != null && stats.coins() != null) {
            for (CoinStatResponse coinStat : stats.coins()) {
                lastSafeCoins.add(new SafeCoinResponse(
                        coinStat.coin().name(),              // id: "BTC"
                        coinStat.initial(),                  // initialPrice
                        coinStat.safe(),                     // currentPrice
                        coinStat.safeUpdateCount(),          // updateCount
                        coinStat.safeLastDelta(),            // Gerçek logdaki son delta!
                        coinStat.safeLastUpdatedBy()         // Logda basılan o gerçek worker ismi (örn: "worker-5")!
                ));
            }
        }
    }

    public SimulationResultResponse getLastStats() {
        return lastStats.get();
    }

    public List<SafeCoinResponse> getLastSafeCoins() {
        return List.copyOf(lastSafeCoins);
    }
}