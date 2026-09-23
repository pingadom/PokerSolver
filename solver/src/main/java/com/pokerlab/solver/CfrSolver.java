package com.pokerlab.solver;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Alternating, full-tree CFR. Suitable for correctness fixtures and small games. */
public final class CfrSolver<S> {
    public enum Variant {
        VANILLA,
        CFR_PLUS
    }

    private static final double CHANCE_TOLERANCE = 1e-9;
    private final CfrGame<S> game;
    private final Variant variant;
    private final Map<String, InformationSet> informationSets = new LinkedHashMap<>();
    private final Map<String, double[]> iterationStrategies = new LinkedHashMap<>();

    public CfrSolver(CfrGame<S> game) {
        this(game, Variant.VANILLA);
    }

    public CfrSolver(CfrGame<S> game, Variant variant) {
        this.game = Objects.requireNonNull(game, "game");
        this.variant = Objects.requireNonNull(variant, "variant");
    }

    public CfrSolution solve(int iterations) {
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        informationSets.clear();
        for (int iteration = 0; iteration < iterations; iteration++) {
            iterationStrategies.clear();
            traverse(game.initialState(), 1, 1, 1, 0, iteration + 1);
            // Aggregate all histories at each information set before applying regret-matching+.
            if (variant == Variant.CFR_PLUS)
                informationSets.values().forEach(InformationSet::clipNegativeRegrets);
            iterationStrategies.clear();
            traverse(game.initialState(), 1, 1, 1, 1, iteration + 1);
            if (variant == Variant.CFR_PLUS)
                informationSets.values().forEach(InformationSet::clipNegativeRegrets);
        }
        Map<String, Map<String, Double>> average = new LinkedHashMap<>();
        informationSets.forEach((key, node) -> average.put(key, node.averageStrategy()));
        return new CfrSolution(iterations, Map.copyOf(average));
    }

    private double traverse(
            S state,
            double reach0,
            double reach1,
            double chanceReach,
            int target,
            int iterationWeight) {
        if (game.isTerminal(state)) {
            double utility = game.terminalUtility(state);
            if (!Double.isFinite(utility)) throw new IllegalArgumentException("Non-finite payoff");
            return target == 0 ? utility : -utility;
        }
        int player = game.currentPlayer(state);
        if (player == -1) {
            List<ChanceOutcome<S>> outcomes = game.chanceOutcomes(state);
            if (outcomes.isEmpty()) throw new IllegalArgumentException("Empty chance node");
            double sum = outcomes.stream().mapToDouble(ChanceOutcome::probability).sum();
            if (Math.abs(sum - 1) > CHANCE_TOLERANCE)
                throw new IllegalArgumentException("Chance probabilities must sum to one");
            double utility = 0;
            for (ChanceOutcome<S> outcome : outcomes) {
                utility +=
                        outcome.probability()
                                * traverse(
                                        outcome.state(),
                                        reach0,
                                        reach1,
                                        chanceReach * outcome.probability(),
                                        target,
                                        iterationWeight);
            }
            return utility;
        }
        if (player != 0 && player != 1)
            throw new IllegalArgumentException("Expected player 0, player 1, or chance");
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
            double probability = strategy[index];
            actionUtilities[index] =
                    traverse(
                            game.afterAction(state, actions.get(index)),
                            player == 0 ? reach0 * probability : reach0,
                            player == 1 ? reach1 * probability : reach1,
                            chanceReach,
                            target,
                            iterationWeight);
            nodeUtility += probability * actionUtilities[index];
        }
        if (player == target) {
            double opponentReach = player == 0 ? reach1 : reach0;
            double ownReach = player == 0 ? reach0 : reach1;
            node.accumulate(
                    strategy,
                    actionUtilities,
                    nodeUtility,
                    opponentReach * chanceReach,
                    ownReach * chanceReach,
                    variant == Variant.CFR_PLUS ? iterationWeight : 1);
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
            if (positiveSum == 0) {
                Arrays.fill(strategy, 1.0 / strategy.length);
            } else {
                for (int index = 0; index < strategy.length; index++)
                    strategy[index] /= positiveSum;
            }
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
            for (int index = 0; index < actions.size(); index++) {
                result.put(
                        actions.get(index),
                        sum == 0 ? 1.0 / actions.size() : strategySum[index] / sum);
            }
            return Map.copyOf(result);
        }
    }
}
