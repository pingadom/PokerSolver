package com.pokerlab.solver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Alternating full-tree regret matching for small multi-player research games. Unlike two-player
 * zero-sum CFR, a small regret value here does not certify a Nash equilibrium.
 */
public final class MultiPlayerCfrSolver<S> {
    private static final double CHANCE_TOLERANCE = 1e-9;
    private final MultiPlayerCfrGame<S> game;
    private final CfrSolver.Variant variant;
    private final int players;
    private final Map<String, InformationSet> informationSets = new LinkedHashMap<>();
    private final Map<String, double[]> iterationStrategies = new LinkedHashMap<>();

    public MultiPlayerCfrSolver(MultiPlayerCfrGame<S> game, CfrSolver.Variant variant) {
        this.game = Objects.requireNonNull(game, "game");
        this.variant = Objects.requireNonNull(variant, "variant");
        this.players = game.playerCount();
        if (players < 2 || players > 6)
            throw new IllegalArgumentException("Expected two to six players");
    }

    public CfrSolution solve(int iterations) {
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        informationSets.clear();
        for (int iteration = 1; iteration <= iterations; iteration++) {
            for (int target = 0; target < players; target++) {
                iterationStrategies.clear();
                double[] reach = new double[players];
                Arrays.fill(reach, 1);
                traverse(game.initialState(), reach, 1, target, iteration);
                if (variant == CfrSolver.Variant.CFR_PLUS)
                    informationSets.values().forEach(InformationSet::clipNegativeRegrets);
            }
        }
        Map<String, Map<String, Double>> average = new LinkedHashMap<>();
        informationSets.forEach((key, node) -> average.put(key, node.averageStrategy()));
        return new CfrSolution(iterations, Map.copyOf(average));
    }

    private double traverse(
            S state, double[] reach, double chanceReach, int target, int iterationWeight) {
        if (game.isTerminal(state)) {
            double[] utilities = game.terminalUtilities(state);
            if (utilities == null || utilities.length != players)
                throw new IllegalArgumentException("Terminal utility count must match players");
            for (double utility : utilities)
                if (!Double.isFinite(utility))
                    throw new IllegalArgumentException("Non-finite terminal utility");
            return utilities[target];
        }
        int player = game.currentPlayer(state);
        if (player == -1) {
            List<ChanceOutcome<S>> outcomes = game.chanceOutcomes(state);
            if (outcomes.isEmpty()) throw new IllegalArgumentException("Empty chance node");
            double sum = outcomes.stream().mapToDouble(ChanceOutcome::probability).sum();
            if (Math.abs(sum - 1) > CHANCE_TOLERANCE)
                throw new IllegalArgumentException("Chance probabilities must sum to one");
            double utility = 0;
            for (ChanceOutcome<S> outcome : outcomes)
                utility +=
                        outcome.probability()
                                * traverse(
                                        outcome.state(),
                                        reach,
                                        chanceReach * outcome.probability(),
                                        target,
                                        iterationWeight);
            return utility;
        }
        if (player < 0 || player >= players)
            throw new IllegalArgumentException("Invalid player index");
        List<String> actions = List.copyOf(game.legalActions(state));
        if (actions.isEmpty()
                || actions.stream().anyMatch(a -> a == null || a.isBlank())
                || new HashSet<>(actions).size() != actions.size())
            throw new IllegalArgumentException("Decision node needs named actions");
        String informationSet = game.informationSet(state);
        if (informationSet == null || informationSet.isBlank())
            throw new IllegalArgumentException("Decision node needs an information set");
        String key = player + ":" + informationSet;
        InformationSet node =
                informationSets.computeIfAbsent(key, ignored -> new InformationSet(actions));
        node.requireActions(actions);
        double[] strategy =
                iterationStrategies.computeIfAbsent(key, ignored -> node.currentStrategy());
        double[] actionUtilities = new double[actions.size()];
        double nodeUtility = 0;
        for (int index = 0; index < actions.size(); index++) {
            double[] nextReach = reach.clone();
            nextReach[player] *= strategy[index];
            actionUtilities[index] =
                    traverse(
                            game.afterAction(state, actions.get(index)),
                            nextReach,
                            chanceReach,
                            target,
                            iterationWeight);
            nodeUtility += strategy[index] * actionUtilities[index];
        }
        if (player == target) {
            double counterfactualReach = chanceReach;
            for (int index = 0; index < players; index++)
                if (index != player) counterfactualReach *= reach[index];
            node.accumulate(
                    strategy,
                    actionUtilities,
                    nodeUtility,
                    counterfactualReach,
                    reach[player] * chanceReach,
                    variant == CfrSolver.Variant.CFR_PLUS ? iterationWeight : 1);
        }
        return nodeUtility;
    }

    private static final class InformationSet {
        private final List<String> actions;
        private final double[] regret;
        private final double[] strategySum;

        private InformationSet(List<String> actions) {
            this.actions = actions;
            regret = new double[actions.size()];
            strategySum = new double[actions.size()];
        }

        private void requireActions(List<String> candidate) {
            if (!actions.equals(candidate))
                throw new IllegalArgumentException(
                        "Inconsistent legal actions within an information set");
        }

        private double[] currentStrategy() {
            double[] strategy = new double[actions.size()];
            double positiveSum = 0;
            for (int index = 0; index < regret.length; index++) {
                strategy[index] = Math.max(0, regret[index]);
                positiveSum += strategy[index];
            }
            if (positiveSum == 0) Arrays.fill(strategy, 1.0 / strategy.length);
            else
                for (int index = 0; index < strategy.length; index++)
                    strategy[index] /= positiveSum;
            return strategy;
        }

        private void accumulate(
                double[] strategy,
                double[] actionUtilities,
                double nodeUtility,
                double counterfactualReach,
                double ownReach,
                int averagingWeight) {
            for (int index = 0; index < strategy.length; index++) {
                regret[index] += counterfactualReach * (actionUtilities[index] - nodeUtility);
                strategySum[index] += averagingWeight * ownReach * strategy[index];
            }
        }

        private void clipNegativeRegrets() {
            for (int index = 0; index < regret.length; index++)
                regret[index] = Math.max(0, regret[index]);
        }

        private Map<String, Double> averageStrategy() {
            double sum = Arrays.stream(strategySum).sum();
            Map<String, Double> result = new LinkedHashMap<>();
            for (int index = 0; index < actions.size(); index++)
                result.put(
                        actions.get(index),
                        sum == 0 ? 1.0 / actions.size() : strategySum[index] / sum);
            return Map.copyOf(result);
        }
    }
}
