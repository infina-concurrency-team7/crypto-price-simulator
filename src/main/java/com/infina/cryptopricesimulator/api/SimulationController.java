package com.infina.cryptopricesimulator.api;

import com.infina.cryptopricesimulator.dto.SafeCoinResponse;
import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import com.infina.cryptopricesimulator.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@Validated
@RequiredArgsConstructor
@Tag(name = "Kripto Simülatör API", description = "Simülasyon yönetimi ve coin durum sorguları")
public class SimulationController {

    private final SimulationStateHolder stateHolder;

    private final SimulationService simulationService;

    @PostMapping("/simulate")
    @Operation(summary = "Yeni bir simülasyon başlatır", description = "Belirtilen parametrelerle güvenli ve güvensiz simülasyonları çalıştırır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simülasyon başarıyla tamamladı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz parametere girişi (updates 1-100k, workers 1-16)"),
            @ApiResponse(responseCode = "409", description = "Halihazırda çalışan bir simülasyon var")
    })
    public ResponseEntity<SimulationResultResponse> runSimulation(
            @RequestParam @Min(1) @Max(100000) int updates,
            @RequestParam @Min(1) @Max(16) int workers,
            @RequestParam(required = false) Long seed
    ){
        long activeSeed = (seed != null) ? seed : System.currentTimeMillis();

        SimulationResultResponse simulation = simulationService.runSimulation(workers,updates,activeSeed);

        // Elimizdeki CoinStatResponse listesini SafeCoinResponse listesine dönüştürüyoruz
        List<SafeCoinResponse> safeCoins = simulation.coins().stream()
                .map(coinStat -> new SafeCoinResponse(
                        coinStat.coin().name(),            // id: "BTC"
                        coinStat.initial(),                // initialPrice
                        coinStat.safe(),                   // currentPrice (Safe simülasyon sonucu)
                        coinStat.safeUpdateCount(),        // updateCount (Safe simülasyon güncelleme sayısı)
                        coinStat.safeLastDelta(),          // safeLastDelta (DTO'ya yeni eklediğin alan)
                        coinStat.safeLastUpdatedBy()       // safeLastUpdatedBy (DTO'ya yeni eklediğin alan)
                ))
                .toList();

        //Oluşturduğumuz bu uyumlu listeyi StateHolder'a gönderiyoruz
        stateHolder.updateResults(simulation, safeCoins);

        stateHolder.updateResults(simulation, safeCoins);

        return ResponseEntity.ok(simulation);

    }


    @GetMapping("/stats")
    @Operation(
            summary = "Son tamamlanan simülasyon istatistiklerini getirir",
            description = "Veritabanı kullanılmadığı için ..."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başarılı"),
            @ApiResponse(responseCode = "404", description = "Sistemde henüz tamamlanmış bir simülasyon yok")
    })
    public ResponseEntity<SimulationResultResponse> getStats(){

        SimulationResultResponse stats = stateHolder.getLastStats();

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/coins")
    @Operation(
            summary = "Son simülasyonun başarılı güvenli coin durumlarını listeler",
            description = "Son simülasyon tamamlandıktan sonra oluşan coin durumlarını liste halinde döner."
    )
    public ResponseEntity<List<SafeCoinResponse>> getCoins(){
        List<SafeCoinResponse> coins = stateHolder.getLastSafeCoins();

        return ResponseEntity.ok(coins);

    }

}
