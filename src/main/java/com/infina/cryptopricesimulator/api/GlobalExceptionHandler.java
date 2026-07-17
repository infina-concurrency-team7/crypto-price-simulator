package com.infina.cryptopricesimulator.api;

import com.infina.cryptopricesimulator.dto.ErrorResponse;
import com.infina.cryptopricesimulator.api.exception.SimulationAlreadyRunningException;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // PARAMETRE KONTROLÜ - updates (1-100k) veya workers (1-16) hatalı girilirse
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(ConstraintViolationException e) {
        Map<String, Object> errorDetail = new HashMap<>();

        e.getConstraintViolations().forEach(violation -> {
            String propertyPath = violation.getPropertyPath().toString();
            String paramName = propertyPath.substring(propertyPath.lastIndexOf('.') + 1);
            errorDetail.put(paramName, violation.getMessage());
        });

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now().toString(),
                HttpStatus.BAD_REQUEST.value(),
                "Bad Request",
                "Geçersiz parametre girişi yapıldı.",
                errorDetail
        );

        return  ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);

    }

    // AYNI ANDA İKİ SİMÜLASYON İSTEĞİ (HTTP 409 - CONFLICT)
    @ExceptionHandler(SimulationAlreadyRunningException.class)
    public ResponseEntity<Map<String, Object>> handleSimulationAlreadyRunning(SimulationAlreadyRunningException e){
        // Yönergede istenen format: { "message": "Another simulation is already running." }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", e.getMessage()));
    }

//BEKLENMEYEN SİSTEM HATALARI (HTTP 500 - INTERNAL SERVER ERROR)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneralException(Exception e){

        ErrorResponse response = new ErrorResponse(
                LocalDateTime.now().toString(),
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Internal Server Error",
                "Beklenmeyen bir iç hata oluştu.",
                Map.of("message", e.getMessage())
        );

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

}
