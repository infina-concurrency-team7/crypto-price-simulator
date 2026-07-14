package com.infina.cryptopricesimulator.api.exception;

public class SimulationNotFoundException extends RuntimeException{
    public SimulationNotFoundException(){
        super("No completed simulation found. Please run a simulation first.");
    }
}
