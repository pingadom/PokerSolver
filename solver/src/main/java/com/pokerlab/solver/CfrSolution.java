package com.pokerlab.solver;

import java.util.Map;

/** Average strategy produced by alternating vanilla CFR, not a certified equilibrium. */
public record CfrSolution(int iterations, Map<String, Map<String, Double>> strategy) {
    public Map<String, Double> at(int player, String informationSet) {
        return strategy.get(player + ":" + informationSet);
    }
}
