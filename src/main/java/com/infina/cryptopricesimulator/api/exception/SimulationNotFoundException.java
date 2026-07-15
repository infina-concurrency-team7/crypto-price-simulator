package com.infina.cryptopricesimulator.api.exception;

public class SimulationNotFoundException extends RuntimeException{
    public SimulationNotFoundException(String message) {
        super(message);
    }
}
