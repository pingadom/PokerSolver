package com.pokerlab.solver;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable validation-only strategy for the exact-deck three-street fixture. */
public record FlopTurnRiverSolutionPack(
        String schemaVersion,
        String solverVersion,
        String publicationStatus,
        String generatedAt,
        FlopTurnRiverSpot spot,
        String spotHash,
        int iterations,
        double gameGapBb,
        CfrSolution solution) {
    public static final String SCHEMA_VERSION = "flop-turn-river-single-bet-pack/v1";
    public static final String CFR_PLUS_SOLVER_VERSION = "alternating-cfr-plus/v1";
    public static final String VALIDATION_ONLY = "VALIDATION_ONLY";
    private static final double TOLERANCE = 1e-8;

    public FlopTurnRiverSolutionPack {
        Objects.requireNonNull(spot, "spot");
        Objects.requireNonNull(solution, "solution");
    }

    public void validate() {
        if (!SCHEMA_VERSION.equals(schemaVersion)
                || !CFR_PLUS_SOLVER_VERSION.equals(solverVersion)
                || !VALIDATION_ONLY.equals(publicationStatus))
            throw new IllegalArgumentException("Unsupported flop pack or solver version");
        try {
            Instant.parse(generatedAt);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw new IllegalArgumentException("Invalid flop pack generation timestamp", exception);
        }
        if (!spot.contentHash().equals(spotHash))
            throw new IllegalArgumentException("Flop pack spot hash does not match inputs");
        if (!spot.withFullTurnDeck().contentHash().equals(spotHash))
            throw new IllegalArgumentException("Flop pack requires the full physical turn deck");
        if (iterations < 1
                || solution.iterations() != iterations
                || !Double.isFinite(gameGapBb)
                || gameGapBb < 0)
            throw new IllegalArgumentException("Invalid flop pack generation metadata");

        FlopTurnRiverGame game = spot.game();
        Map<String, List<String>> expected = new HashMap<>();
        collectExpected(game, game.initialState(), expected);
        if (!expected.keySet().equals(solution.strategy().keySet()))
            throw new IllegalArgumentException("Flop pack information sets do not match game");
        for (var entry : expected.entrySet()) {
            Map<String, Double> actions = solution.strategy().get(entry.getKey());
            if (actions == null || !actions.keySet().equals(Set.copyOf(entry.getValue())))
                throw new IllegalArgumentException("Incorrect flop pack legal actions");
            double sum = 0;
            for (double probability : actions.values()) {
                if (!Double.isFinite(probability) || probability < 0 || probability > 1)
                    throw new IllegalArgumentException("Invalid flop pack action probability");
                sum += probability;
            }
            if (Math.abs(sum - 1) > TOLERANCE)
                throw new IllegalArgumentException(
                        "Flop pack action probabilities do not sum to one");
        }
        double measured = HeadsUpBestResponse.assess(game, solution).gap();
        if (Math.abs(measured - gameGapBb) > TOLERANCE)
            throw new IllegalArgumentException(
                    "Flop pack best-response gap does not match solution");
    }

    private static void collectExpected(
            FlopTurnRiverGame game,
            FlopTurnRiverGame.State state,
            Map<String, List<String>> expected) {
        if (game.isTerminal(state)) return;
        int player = game.currentPlayer(state);
        if (player == -1) {
            for (ChanceOutcome<FlopTurnRiverGame.State> outcome : game.chanceOutcomes(state))
                collectExpected(game, outcome.state(), expected);
            return;
        }
        List<String> actions = game.legalActions(state);
        String key = player + ":" + game.informationSet(state);
        List<String> previous = expected.putIfAbsent(key, actions);
        if (previous != null && !previous.equals(actions))
            throw new IllegalArgumentException("Inconsistent flop pack information set");
        for (String action : actions)
            collectExpected(game, game.afterAction(state, action), expected);
    }
}
