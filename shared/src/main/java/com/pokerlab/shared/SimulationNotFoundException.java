package com.pokerlab.shared;

import java.util.UUID;

public class SimulationNotFoundException extends RuntimeException {
    public SimulationNotFoundException(UUID id) {
        super("Simulation not found: " + id);
    }
}
