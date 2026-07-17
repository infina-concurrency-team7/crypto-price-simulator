package com.infina.cryptopricesimulator.api.controller;

import com.infina.cryptopricesimulator.api.controller.docs.SimulationApi;
import com.infina.cryptopricesimulator.dto.SafeCoinResponse;
import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import com.infina.cryptopricesimulator.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Validated
@RequiredArgsConstructor
public class SimulationController implements SimulationApi {

    private final SimulationService simulationService;

    @Override
    @PostMapping("/simulate")
    public ResponseEntity<SimulationResultResponse> runSimulation(int updates, int workers, long seed) {
        SimulationResultResponse result = simulationService.runSimulation(workers, updates, seed);
        return ResponseEntity.ok(result);
    }

    @Override
    @GetMapping("/stats")
    public ResponseEntity<SimulationResultResponse> getStats() {
        return ResponseEntity.ok(simulationService.getLastResult());
    }

    @Override
    @GetMapping("/coins")
    public ResponseEntity<List<SafeCoinResponse>> getCoins() {
        return ResponseEntity.ok(simulationService.getLastSafeCoins());
    }
}
