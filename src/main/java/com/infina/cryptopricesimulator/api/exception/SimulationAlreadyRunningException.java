package com.infina.cryptopricesimulator.api.exception;

public class SimulationAlreadyRunningException extends RuntimeException {

    public SimulationAlreadyRunningException() {
        super("Another simulation is already running.");
    }
}
