package com.pokerlab.solver;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Exact information-set best responses for the fixed-board, single-bet river tree. */
public final class RiverBetBestResponse {
    public record Report(
            double firstBestResponse, double secondBestResponse, double profileValue, double gap) {}

    private RiverBetBestResponse() {}

    public static Report assess(RiverBetGame game, CfrSolution solution) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        Map<String, double[]> firstActions = new LinkedHashMap<>();
        Map<String, double[]> secondActions = new LinkedHashMap<>();

        for (ChanceOutcome<RiverBetGame.State> outcome : game.chanceOutcomes(game.initialState())) {
            RiverBetGame.State deal = outcome.state();
            RiverBetGame.State afterFirstCheck = game.afterAction(deal, "k");
            RiverBetGame.State afterFirstBet = game.afterAction(deal, "b");
            RiverBetGame.State afterSecondBet = game.afterAction(afterFirstCheck, "b");
            double weight = outcome.probability();
            double firstBet = probability(solution, 0, game.informationSet(deal), "b", "k");
            double secondBet =
                    probability(solution, 1, game.informationSet(afterFirstCheck), "b", "k");
            double secondCall =
                    probability(solution, 1, game.informationSet(afterFirstBet), "c", "f");
            double firstCall =
                    probability(solution, 0, game.informationSet(afterSecondBet), "c", "f");
            double checked = game.terminalUtility(game.afterAction(afterFirstCheck, "k"));
            double firstWinsFold = game.terminalUtility(game.afterAction(afterFirstBet, "f"));
            double firstBetCalled = game.terminalUtility(game.afterAction(afterFirstBet, "c"));
            double firstLosesFold = game.terminalUtility(game.afterAction(afterSecondBet, "f"));
            double secondBetCalled = game.terminalUtility(game.afterAction(afterSecondBet, "c"));

            // Player zero chooses at the root and, separately, after check-bet. At the root,
            // include the optimal check-bet response for its private combo only once.
            double[] first =
                    firstActions.computeIfAbsent(deal.first().key(), ignored -> new double[4]);
            first[0] += weight * (secondCall * firstBetCalled + (1 - secondCall) * firstWinsFold);
            first[1] += weight * (1 - secondBet) * checked;
            first[2] += weight * secondBet * secondBetCalled;
            first[3] += weight * secondBet * firstLosesFold;

            // Player one chooses independently after a bet and after a check, minimizing
            // player-zero utility against the fixed player-zero strategy.
            double[] second =
                    secondActions.computeIfAbsent(deal.second().key(), ignored -> new double[4]);
            second[0] += weight * firstBet * firstBetCalled;
            second[1] += weight * firstBet * firstWinsFold;
            second[2] += weight * (1 - firstBet) * checked;
            second[3] +=
                    weight
                            * (1 - firstBet)
                            * (firstCall * secondBetCalled + (1 - firstCall) * firstLosesFold);
        }

        double firstBest =
                firstActions.values().stream()
                        .mapToDouble(
                                values ->
                                        Math.max(
                                                values[0],
                                                values[1] + Math.max(values[2], values[3])))
                        .sum();
        double secondBest =
                secondActions.values().stream()
                        .mapToDouble(
                                values ->
                                        Math.min(values[0], values[1])
                                                + Math.min(values[2], values[3]))
                        .sum();
        double profile = StrategyEvaluator.playerZeroUtility(game, solution);
        double gap = firstBest - secondBest;
        if (gap < -1e-8 || profile > firstBest + 1e-8 || profile < secondBest - 1e-8)
            throw new IllegalStateException("Inconsistent river best-response bounds");
        return new Report(firstBest, secondBest, profile, Math.max(0, gap));
    }

    private static double probability(
            CfrSolution solution,
            int player,
            String informationSet,
            String action,
            String alternative) {
        Map<String, Double> actions = solution.at(player, informationSet);
        if (actions == null || actions.size() != 2)
            throw new IllegalArgumentException("Missing two-action strategy: " + informationSet);
        Double selected = actions.get(action);
        Double other = actions.get(alternative);
        if (selected == null
                || other == null
                || !Double.isFinite(selected)
                || !Double.isFinite(other)
                || selected < 0
                || other < 0
                || Math.abs(selected + other - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid strategy probabilities: " + informationSet);
        return selected;
    }
}
