package com.pokerlab.solver;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable validation-only solution for one bounded turn-to-river game. */
public record TurnRiverSolutionPack(
        String schemaVersion,
        String solverVersion,
        String publicationStatus,
        String generatedAt,
        TurnRiverSpot spot,
        String spotHash,
        int iterations,
        double gameGapBb,
        CfrSolution solution) {
    public static final String SCHEMA_VERSION = "turn-river-single-bet-pack/v1";
    public static final String CFR_PLUS_SOLVER_VERSION = "alternating-cfr-plus/v1";
    public static final String VALIDATION_ONLY = "VALIDATION_ONLY";
    private static final double TOLERANCE = 1e-8;

    public TurnRiverSolutionPack {
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(solution, "solution");
    }

    public void validate() {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || !CFR_PLUS_SOLVER_VERSION.equals(solverVersion)
                || !VALIDATION_ONLY.equals(publicationStatus))
            throw new IllegalArgumentException("Unsupported turn-river pack or solver version");
        try {
            Instant.parse(generatedAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException(
                    "Invalid turn-river generation timestamp", exception);
        }
        if (!spot.contentHash().equals(spotHash))
            throw new IllegalArgumentException("Turn-river spot hash does not match inputs");
        if (iterations < 1
                || solution.iterations() != iterations
                || !Double.isFinite(gameGapBb)
                || gameGapBb < 0)
            throw new IllegalArgumentException("Invalid turn-river generation metadata");

        TurnRiverGame game = spot.game();
        Map<String, List<String>> expected = new HashMap<>();
        collectExpected(game, game.initialState(), expected);
        if (!expected.keySet().equals(solution.strategy().keySet()))
            throw new IllegalArgumentException(
                    "Turn-river solution information sets do not match game");
        for (var entry : expected.entrySet()) {
            Map<String, Double> actions = solution.strategy().get(entry.getKey());
            if (actions == null || !actions.keySet().equals(Set.copyOf(entry.getValue())))
                throw new IllegalArgumentException("Incorrect turn-river legal actions");
            double sum = 0;
            for (double probability : actions.values()) {
                if (!Double.isFinite(probability) || probability < 0 || probability > 1)
                    throw new IllegalArgumentException("Invalid turn-river action probability");
                sum += probability;
            }
            if (Math.abs(sum - 1) > TOLERANCE)
                throw new IllegalArgumentException(
                        "Turn-river action probabilities do not sum to one");
        }
        double measured = HeadsUpBestResponse.assess(game, solution).gap();
        if (Math.abs(measured - gameGapBb) > TOLERANCE)
            throw new IllegalArgumentException(
                    "Turn-river best-response gap does not match solution");
    }

    private static void collectExpected(
            TurnRiverGame game, TurnRiverGame.State state, Map<String, List<String>> expected) {
        if (game.isTerminal(state)) return;
        int player = game.currentPlayer(state);
        if (player == -1) {
            for (ChanceOutcome<TurnRiverGame.State> outcome : game.chanceOutcomes(state))
                collectExpected(game, outcome.state(), expected);
            return;
        }
        List<String> actions = game.legalActions(state);
        String key = player + ":" + game.informationSet(state);
        List<String> previous = expected.putIfAbsent(key, actions);
        if (previous != null && !previous.equals(actions))
            throw new IllegalArgumentException("Inconsistent turn-river information set");
        for (String action : actions)
            collectExpected(game, game.afterAction(state, action), expected);
    }
}
