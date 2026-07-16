package com.infina.cryptopricesimulator.controller;

import com.infina.cryptopricesimulator.dto.SimulationResultResponse;
import com.infina.cryptopricesimulator.service.SimulationService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class SimulationController {

    private final SimulationService simulationService;

    @PostMapping("/simulate")
    public ResponseEntity<SimulationResultResponse> simulate(
            @RequestParam(defaultValue = "4") int workers,
            @RequestParam(defaultValue = "10000") int updates,
            @RequestParam(defaultValue = "42") long seed) {

        SimulationResultResponse result = simulationService.runSimulation(workers, updates, seed);
        return ResponseEntity.ok(result);
    }
}
