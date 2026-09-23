package com.pokerlab.solver;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Computes exact expected player-zero utility for a finite strategy profile. */
public final class StrategyEvaluator {
    private StrategyEvaluator() {}

    public static <S> double playerZeroUtility(CfrGame<S> game, CfrSolution solution) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        return evaluate(game, solution, game.initialState());
    }

    private static <S> double evaluate(CfrGame<S> game, CfrSolution solution, S state) {
        if (game.isTerminal(state)) return game.terminalUtility(state);
        int player = game.currentPlayer(state);
        if (player == -1) {
            double utility = 0;
            for (ChanceOutcome<S> outcome : game.chanceOutcomes(state))
                utility += outcome.probability() * evaluate(game, solution, outcome.state());
            return utility;
        }
        List<String> actions = game.legalActions(state);
        Map<String, Double> strategy = solution.at(player, game.informationSet(state));
        if (strategy == null)
            throw new IllegalArgumentException("Missing strategy for information set");
        double utility = 0;
        for (String action : actions) {
            Double probability = strategy.get(action);
            if (probability == null || !Double.isFinite(probability) || probability < 0)
                throw new IllegalArgumentException("Missing or invalid action probability");
            utility += probability * evaluate(game, solution, game.afterAction(state, action));
        }
        return utility;
    }
}
