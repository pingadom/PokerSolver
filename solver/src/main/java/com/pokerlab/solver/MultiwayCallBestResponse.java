package com.pokerlab.solver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exact unilateral response to the saved profile in the bounded call/fold game. Each responder acts
 * once; its decision values can therefore be aggregated by private combo and public history. This
 * is a deviation check for this game's precomputed payoff table, not a general GTO proof.
 */
public final class MultiwayCallBestResponse {
    public record Report(
            List<Double> profileUtilitiesBb,
            List<Double> bestResponseUtilitiesBb,
            List<Double> deviationGainsBb,
            double nashConvBb) {
        public Report {
            profileUtilitiesBb = List.copyOf(profileUtilitiesBb);
            bestResponseUtilitiesBb = List.copyOf(bestResponseUtilitiesBb);
            deviationGainsBb = List.copyOf(deviationGainsBb);
        }
    }

    private MultiwayCallBestResponse() {}

    public static Report assess(MultiwayPreflopCallGame game, CfrSolution solution) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        double[] profile = MultiPlayerStrategyEvaluator.utilities(game, solution);
        List<Double> values = new java.util.ArrayList<>();
        List<Double> best = new java.util.ArrayList<>();
        List<Double> gains = new java.util.ArrayList<>();
        double nashConv = 0;
        for (int target = 0; target < game.playerCount(); target++) {
            values.add(profile[target]);
            double response;
            if (target == 0) response = profile[0]; // The aggressor is already all-in.
            else {
                Map<String, double[]> actionValues = new LinkedHashMap<>();
                collect(game, solution, game.initialState(), target, 1, actionValues);
                response =
                        actionValues.values().stream()
                                .mapToDouble(actions -> Math.max(actions[0], actions[1]))
                                .sum();
            }
            double gain = response - profile[target];
            if (gain < -1e-8)
                throw new IllegalStateException("Best response cannot be worse than profile");
            gain = Math.max(0, gain);
            best.add(response);
            gains.add(gain);
            nashConv += gain;
        }
        return new Report(values, best, gains, nashConv);
    }

    private static void collect(
            MultiwayPreflopCallGame game,
            CfrSolution solution,
            MultiwayPreflopCallGame.State state,
            int target,
            double reach,
            Map<String, double[]> values) {
        if (game.isTerminal(state)) return;
        int player = game.currentPlayer(state);
        if (player == -1) {
            for (var outcome : game.chanceOutcomes(state))
                collect(
                        game,
                        solution,
                        outcome.state(),
                        target,
                        reach * outcome.probability(),
                        values);
        } else if (player == target) {
            double[] actions =
                    values.computeIfAbsent(game.informationSet(state), ignored -> new double[2]);
            actions[0] +=
                    reach * continuation(game, solution, game.afterAction(state, "c"), target);
            actions[1] +=
                    reach * continuation(game, solution, game.afterAction(state, "f"), target);
        } else {
            for (String action : game.legalActions(state))
                collect(
                        game,
                        solution,
                        game.afterAction(state, action),
                        target,
                        reach
                                * MultiPlayerStrategyEvaluator.probability(
                                        game, solution, state, action),
                        values);
        }
    }

    private static double continuation(
            MultiwayPreflopCallGame game,
            CfrSolution solution,
            MultiwayPreflopCallGame.State state,
            int target) {
        if (game.isTerminal(state)) return game.terminalUtilities(state)[target];
        int player = game.currentPlayer(state);
        if (player == target)
            throw new IllegalStateException("A responder must act only once in this game");
        double utility = 0;
        for (String action : game.legalActions(state))
            utility +=
                    MultiPlayerStrategyEvaluator.probability(game, solution, state, action)
                            * continuation(game, solution, game.afterAction(state, action), target);
        return utility;
    }
}
