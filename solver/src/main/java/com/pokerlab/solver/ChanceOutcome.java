package com.pokerlab.solver;

public record ChanceOutcome<S>(S state, double probability) {
    public ChanceOutcome {
        if (state == null || !Double.isFinite(probability) || probability <= 0 || probability > 1) {
            throw new IllegalArgumentException(
                    "Chance outcome requires a state and probability in (0, 1]");
        }
    }
}
