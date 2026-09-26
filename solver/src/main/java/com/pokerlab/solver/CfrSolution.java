package com.pokerlab.solver;

import java.util.Map;

/** Average strategy produced by regret matching; not by itself a certified equilibrium. */
public record CfrSolution(int iterations, Map<String, Map<String, Double>> strategy) {
    public CfrSolution {
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        strategy =
                strategy.entrySet().stream()
                        .collect(
                                java.util.stream.Collectors.toUnmodifiableMap(
                                        Map.Entry::getKey, entry -> Map.copyOf(entry.getValue())));
    }

    public Map<String, Double> at(int player, String informationSet) {
        return strategy.get(player + ":" + informationSet);
    }
}
