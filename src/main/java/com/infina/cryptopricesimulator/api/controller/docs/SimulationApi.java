package com.infina.cryptopricesimulator.api.controller.docs;

import com.infina.cryptopricesimulator.dto.SafeCoinResponse;
import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Tag(name = "Kripto Simülatör API", description = "Simülasyon yönetimi ve coin durum sorguları")
public interface SimulationApi {

    @Operation(summary = "Yeni bir simülasyon başlatır",
            description = "Belirtilen parametrelerle güvenli ve güvensiz simülasyonları çalıştırır.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Simülasyon başarıyla tamamladı"),
            @ApiResponse(responseCode = "400", description = "Geçersiz parametre girişi (updates 1-100k, workers 1-16)"),
            @ApiResponse(responseCode = "409", description = "Halihazırda çalışan bir simülasyon var")
    })
    ResponseEntity<SimulationResultResponse> runSimulation(
            @RequestParam @Min(1) @Max(100000) int updates,
            @RequestParam @Min(1) @Max(16) int workers,
            @RequestParam long seed);

    @Operation(summary = "Son tamamlanan simülasyon istatistiklerini getirir")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başarılı"),
            @ApiResponse(responseCode = "404", description = "Sistemde henüz tamamlanmış bir simülasyon yok")
    })
    ResponseEntity<SimulationResultResponse> getStats();

    @Operation(summary = "Son simülasyonun güvenli coin durumlarını listeler")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Başarılı"),
            @ApiResponse(responseCode = "404", description = "Sistemde henüz tamamlanmış bir simülasyon yok")
    })
    ResponseEntity<List<SafeCoinResponse>> getCoins();
}
