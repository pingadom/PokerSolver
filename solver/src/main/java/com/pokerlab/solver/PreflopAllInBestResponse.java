package com.pokerlab.solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Exact best responses to a strategy profile in the bounded all-in game's estimated payoff tree.
 */
public final class PreflopAllInBestResponse {
    public record Report(double firstBestResponse, double secondBestResponse, double gap) {}

    private PreflopAllInBestResponse() {}

    public static Report assess(PreflopAllInGame game, CfrSolution solution) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        Map<String, double[]> firstActions = new LinkedHashMap<>();
        Map<String, double[]> secondActions = new LinkedHashMap<>();
        double firstFoldBaseline = 0;

        for (ChanceOutcome<PreflopAllInGame.State> outcome :
                game.chanceOutcomes(game.initialState())) {
            PreflopAllInGame.State deal = outcome.state();
            PreflopAllInGame.State shove = game.afterAction(deal, "s");
            double probability = outcome.probability();
            double firstShove = actionProbability(solution, 0, game.informationSet(deal), "s", "f");
            double secondCall =
                    actionProbability(solution, 1, game.informationSet(shove), "c", "f");
            double firstFold = game.terminalUtility(game.afterAction(deal, "f"));
            double secondFold = game.terminalUtility(game.afterAction(shove, "f"));
            double called = game.terminalUtility(game.afterAction(shove, "c"));

            double[] firstValues =
                    firstActions.computeIfAbsent(deal.first().key(), ignored -> new double[2]);
            firstValues[0] += probability * (secondCall * called + (1 - secondCall) * secondFold);
            firstValues[1] += probability * firstFold;

            firstFoldBaseline += probability * (1 - firstShove) * firstFold;
            double[] secondValues =
                    secondActions.computeIfAbsent(deal.second().key(), ignored -> new double[2]);
            secondValues[0] += probability * firstShove * called;
            secondValues[1] += probability * firstShove * secondFold;
        }

        double firstBest =
                firstActions.values().stream()
                        .mapToDouble(values -> Math.max(values[0], values[1]))
                        .sum();
        double secondBest =
                firstFoldBaseline
                        + secondActions.values().stream()
                                .mapToDouble(values -> Math.min(values[0], values[1]))
                                .sum();
        double gap = firstBest - secondBest;
        if (gap < -1e-9) throw new IllegalStateException("Best-response gap cannot be negative");
        return new Report(firstBest, secondBest, Math.max(0, gap));
    }

    private static double actionProbability(
            CfrSolution solution,
            int player,
            String informationSet,
            String action,
            String alternative) {
        Map<String, Double> actions = solution.at(player, informationSet);
        if (actions == null || actions.size() != 2)
            throw new IllegalArgumentException("Missing two-action strategy: " + informationSet);
        Double probability = actions.get(action);
        Double other = actions.get(alternative);
        if (probability == null
                || other == null
                || !Double.isFinite(probability)
                || !Double.isFinite(other)
                || probability < 0
                || other < 0
                || Math.abs(probability + other - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid strategy probabilities: " + informationSet);
        return probability;
    }
}
