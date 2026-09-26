package com.pokerlab.solver;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable, validation-only strategy artifact for one bounded river endgame. */
public record RiverSolutionPack(
        String schemaVersion,
        String solverVersion,
        String publicationStatus,
        String generatedAt,
        RiverBetSpot spot,
        String spotHash,
        int iterations,
        double gameGapBb,
        CfrSolution solution) {
    public static final String SCHEMA_VERSION = "river-single-bet-pack/v1";
    public static final String CFR_PLUS_SOLVER_VERSION = "alternating-cfr-plus/v1";
    public static final String VALIDATION_ONLY = "VALIDATION_ONLY";
    private static final double TOLERANCE = 1e-8;

    public RiverSolutionPack {
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(solution, "solution");
    }

    public void validate() {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || !CFR_PLUS_SOLVER_VERSION.equals(solverVersion)
                || !VALIDATION_ONLY.equals(publicationStatus))
            throw new IllegalArgumentException("Unsupported river pack or solver version");
        try {
            Instant.parse(generatedAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid river generation timestamp", exception);
        }
        if (!spot.contentHash().equals(spotHash))
            throw new IllegalArgumentException("River spot hash does not match inputs");
        if (iterations < 1
                || solution.iterations() != iterations
                || !Double.isFinite(gameGapBb)
                || gameGapBb < 0)
            throw new IllegalArgumentException("Invalid river generation metadata");

        Map<String, Set<String>> expected = new HashMap<>();
        for (WeightedCombo combo : spot.firstRange()) {
            expected.put("0:" + combo.key() + ":", Set.of("k", "b"));
            expected.put("0:" + combo.key() + ":kb", Set.of("c", "f"));
        }
        for (WeightedCombo combo : spot.secondRange()) {
            expected.put("1:" + combo.key() + ":k", Set.of("k", "b"));
            expected.put("1:" + combo.key() + ":b", Set.of("c", "f"));
        }
        if (!expected.keySet().equals(solution.strategy().keySet()))
            throw new IllegalArgumentException(
                    "River solution information sets do not match ranges");
        for (var entry : expected.entrySet()) {
            Map<String, Double> actions = solution.strategy().get(entry.getKey());
            if (actions == null || !new HashSet<>(actions.keySet()).equals(entry.getValue()))
                throw new IllegalArgumentException("Incorrect river legal actions");
            double sum = 0;
            for (double probability : actions.values()) {
                if (!Double.isFinite(probability) || probability < 0 || probability > 1)
                    throw new IllegalArgumentException("Invalid river action probability");
                sum += probability;
            }
            if (Math.abs(sum - 1) > TOLERANCE)
                throw new IllegalArgumentException("River action probabilities do not sum to one");
        }
        double measured = RiverBetBestResponse.assess(spot.game(), solution).gap();
        if (Math.abs(measured - gameGapBb) > TOLERANCE)
            throw new IllegalArgumentException("River best-response gap does not match solution");
    }
}
