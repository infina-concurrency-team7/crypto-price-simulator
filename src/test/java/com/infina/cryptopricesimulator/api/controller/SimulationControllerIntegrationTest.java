package com.infina.cryptopricesimulator.api.controller;

import com.infina.cryptopricesimulator.service.SimulationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SimulationController.class)
@Import(SimulationService.class)
class SimulationControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    private static final String API_SIMULATE = "/api/simulate";
    private static final String API_STATS = "/api/stats";
    private static final String API_COINS = "/api/coins";
    private static final String WORKER_REGEX = "^worker-\\d+$";

    @Nested
    @DisplayName("Uçtan Uca Başarılı Simülasyon Akış Testleri")
    class HappyPathTests {

        @Test
        @DisplayName("Tam simülasyon akışı: Simüle Et -> İstatistikleri Al -> Coinleri Al")
        void shouldExecuteFullSimulationWorkflowSuccessfully() throws Exception {

            SimulationParams validParams = new SimulationParams("1000", "4", "42");

            performSimulation(validParams)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.seed", is(42)))
                    .andExpect(jsonPath("$.submittedUpdates", is(1000)))
                    .andExpect(jsonPath("$.workers", is(4)))
                    .andExpect(jsonPath("$.safeInvariantPassed", is(true)))
                    .andExpect(jsonPath("$.coins", hasSize(3)));

            mockMvc.perform(get(API_STATS).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.submittedUpdates", is(1000)))
                    .andExpect(jsonPath("$.safeInvariantPassed", is(true)))
                    .andExpect(jsonPath("$.coins[0].safeLastUpdatedBy", matchesPattern(WORKER_REGEX)));

            mockMvc.perform(get(API_COINS).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)))
                    .andExpect(jsonPath("$[0].id", anyOf(is("BTC"), is("ETH"), is("SOL"))))
                    .andExpect(jsonPath("$[0].currentPrice", notNullValue()))
                    .andExpect(jsonPath("$[0].lastUpdatedBy", matchesPattern(WORKER_REGEX)));
        }
    }

    @Nested
    @DisplayName("Hata ve Sınır Durum Testleri (Validation)")
    class ValidationTests {

        @Test
        @DisplayName("updates=0 ve workers=20 (Sınır dışı) girildiğinde 400 Bad Request dönmeli")
        void shouldReturnBadRequestWhenParamsAreOutsideBounds() throws Exception {
            SimulationParams invalidParams = new SimulationParams("0", "20", "123");

            performSimulation(invalidParams)
                    .andExpect(status().isBadRequest());
        }
    }

    private ResultActions performSimulation(SimulationParams params) throws Exception {
        return mockMvc.perform(post(API_SIMULATE)
                .param("updates", params.updates())
                .param("workers", params.workers())
                .param("seed", params.seed())
                .contentType(MediaType.APPLICATION_JSON));
    }

    private record SimulationParams(String updates, String workers, String seed) {}
}
