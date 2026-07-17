package com.infina.cryptopricesimulator.api.exception;

public class SimulationNotFoundException extends RuntimeException {

    public SimulationNotFoundException() {
        super("No simulation has been completed yet.");
    }

    public SimulationNotFoundException(String message) {
        super(message);
    }
}
