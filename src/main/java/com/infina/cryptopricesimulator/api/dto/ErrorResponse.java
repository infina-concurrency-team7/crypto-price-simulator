package com.infina.cryptopricesimulator.api.dto;

import java.util.Map;

public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        Map<String, Object> details
) {
}
