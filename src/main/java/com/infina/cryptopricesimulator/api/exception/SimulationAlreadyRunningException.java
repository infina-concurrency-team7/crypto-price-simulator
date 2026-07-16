package com.infina.cryptopricesimulator.api.exception;

public class SimulationAlreadyRunningException extends RuntimeException{
    public SimulationAlreadyRunningException(String message) {
        super(message);
    }
}
