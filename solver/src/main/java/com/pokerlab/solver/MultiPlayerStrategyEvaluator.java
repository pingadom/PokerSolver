package com.pokerlab.solver;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Exact expected utilities of a saved profile in a finite precomputed payoff game. */
public final class MultiPlayerStrategyEvaluator {
    private MultiPlayerStrategyEvaluator() {}

    public static <S> double[] utilities(MultiPlayerCfrGame<S> game, CfrSolution solution) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        return evaluate(game, solution, game.initialState());
    }

    static <S> double probability(
            MultiPlayerCfrGame<S> game, CfrSolution solution, S state, String action) {
        int player = game.currentPlayer(state);
        Map<String, Double> strategy = solution.at(player, game.informationSet(state));
        List<String> legal = game.legalActions(state);
        if (strategy == null || strategy.size() != legal.size())
            throw new IllegalArgumentException("Missing strategy for information set");
        double sum = 0;
        for (String candidate : legal) {
            Double value = strategy.get(candidate);
            if (value == null || !Double.isFinite(value) || value < 0 || value > 1)
                throw new IllegalArgumentException("Invalid action probability");
            sum += value;
        }
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("Strategy probabilities must sum to one");
        return strategy.get(action);
    }

    private static <S> double[] evaluate(
            MultiPlayerCfrGame<S> game, CfrSolution solution, S state) {
        if (game.isTerminal(state)) {
            double[] utilities = game.terminalUtilities(state);
            if (utilities == null || utilities.length != game.playerCount())
                throw new IllegalArgumentException("Terminal utility count must match players");
            return utilities;
        }
        double[] expected = new double[game.playerCount()];
        int player = game.currentPlayer(state);
        if (player == -1) {
            for (ChanceOutcome<S> outcome : game.chanceOutcomes(state))
                addWeighted(
                        expected, evaluate(game, solution, outcome.state()), outcome.probability());
        } else {
            for (String action : game.legalActions(state))
                addWeighted(
                        expected,
                        evaluate(game, solution, game.afterAction(state, action)),
                        probability(game, solution, state, action));
        }
        return expected;
    }

    private static void addWeighted(double[] target, double[] values, double weight) {
        for (int index = 0; index < target.length; index++) target[index] += weight * values[index];
    }
}
