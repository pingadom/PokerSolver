package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Chance-sampled CFR response search for one player against a fixed opponent policy. This learns an
 * approximate response; it does not compute a certified best-response upper bound.
 */
public final class FixedOpponentResponseCfr<S> {
    public record Result(
            CfrSolution response,
            long fixedOpponentQueries,
            long missingFixedOpponentQueries,
            int queriedFixedOpponentInformationSets,
            int missingFixedOpponentInformationSets) {
        public double fixedOpponentQuerySupportRate() {
            return fixedOpponentQueries == 0
                    ? 0
                    : (double) (fixedOpponentQueries - missingFixedOpponentQueries)
                            / fixedOpponentQueries;
        }
    }

    private static final class Node {
        final List<String> actions;
        final double[] regrets;
        final double[] averageSum;

        Node(List<String> actions) {
            this.actions = List.copyOf(actions);
            regrets = new double[actions.size()];
            averageSum = new double[actions.size()];
        }

        double[] currentStrategy() {
            double[] strategy = new double[actions.size()];
            double positive = 0;
            for (int index = 0; index < actions.size(); index++) {
                strategy[index] = Math.max(0, regrets[index]);
                positive += strategy[index];
            }
            for (int index = 0; index < actions.size(); index++)
                strategy[index] = positive == 0 ? 1.0 / actions.size() : strategy[index] / positive;
            return strategy;
        }

        Map<String, Double> averageStrategy() {
            double total = 0;
            for (double value : averageSum) total += value;
            Map<String, Double> result = new LinkedHashMap<>();
            for (int index = 0; index < actions.size(); index++)
                result.put(
                        actions.get(index),
                        total == 0 ? 1.0 / actions.size() : averageSum[index] / total);
            return Map.copyOf(result);
        }
    }

    private final CfrGame<S> game;
    private final CfrSolution fixedOpponent;
    private final int target;
    private final long seed;
    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, double[]> iterationStrategies = new HashMap<>();
    private final List<Double> chanceDraws = new ArrayList<>();
    private final Set<String> queriedOpponentKeys = new HashSet<>();
    private final Set<String> missingOpponentKeys = new HashSet<>();
    private SplittableRandom random;
    private long opponentQueries;
    private long missingOpponentQueries;

    public FixedOpponentResponseCfr(
            CfrGame<S> game, CfrSolution fixedOpponent, int target, long seed) {
        this.game = Objects.requireNonNull(game, "game");
        this.fixedOpponent = Objects.requireNonNull(fixedOpponent, "fixedOpponent");
        if (target != 0 && target != 1)
            throw new IllegalArgumentException("Target player must be 0 or 1");
        this.target = target;
        this.seed = seed;
    }

    public Result solve(int iterations) {
        if (iterations < 1 || iterations > 1_000_000)
            throw new IllegalArgumentException("Expected 1-1000000 response iterations");
        nodes.clear();
        queriedOpponentKeys.clear();
        missingOpponentKeys.clear();
        opponentQueries = 0;
        missingOpponentQueries = 0;
        random = new SplittableRandom(seed);
        for (int iteration = 0; iteration < iterations; iteration++) {
            chanceDraws.clear();
            iterationStrategies.clear();
            traverse(game.initialState(), 1, 1, 0);
        }
        Map<String, Map<String, Double>> average = new LinkedHashMap<>();
        nodes.forEach((key, node) -> average.put(key, node.averageStrategy()));
        return new Result(
                new CfrSolution(iterations, average),
                opponentQueries,
                missingOpponentQueries,
                queriedOpponentKeys.size(),
                missingOpponentKeys.size());
    }

    private double traverse(S state, double targetReach, double opponentReach, int chanceDepth) {
        if (game.isTerminal(state)) {
            double utility = game.terminalUtility(state);
            if (!Double.isFinite(utility)) throw new IllegalArgumentException("Non-finite payoff");
            return target == 0 ? utility : -utility;
        }
        int player = game.currentPlayer(state);
        if (player == -1) {
            while (chanceDraws.size() <= chanceDepth) chanceDraws.add(random.nextDouble());
            var outcome = game.sampleChanceOutcome(state, chanceDraws.get(chanceDepth));
            return traverse(outcome.state(), targetReach, opponentReach, chanceDepth + 1);
        }
        if (player != 0 && player != 1)
            throw new IllegalArgumentException("Expected player 0, player 1 or chance");
        var actions = game.legalActions(state);
        String informationSet = game.informationSet(state);
        if (player != target) {
            Map<String, Double> policy = opponentPolicy(player, informationSet, actions);
            double value = 0;
            for (String action : actions) {
                double probability = policy.get(action);
                if (probability > 0)
                    value +=
                            probability
                                    * traverse(
                                            game.afterAction(state, action),
                                            targetReach,
                                            opponentReach * probability,
                                            chanceDepth);
            }
            return value;
        }
        String key = player + ":" + informationSet;
        Node node = nodes.computeIfAbsent(key, ignored -> new Node(actions));
        if (!node.actions.equals(actions))
            throw new IllegalArgumentException("Inconsistent target information-set actions");
        double[] strategy =
                iterationStrategies.computeIfAbsent(key, ignored -> node.currentStrategy());
        double[] values = new double[actions.size()];
        double value = 0;
        for (int index = 0; index < actions.size(); index++) {
            values[index] =
                    traverse(
                            game.afterAction(state, actions.get(index)),
                            targetReach * strategy[index],
                            opponentReach,
                            chanceDepth);
            value += strategy[index] * values[index];
        }
        for (int index = 0; index < actions.size(); index++) {
            node.regrets[index] += opponentReach * (values[index] - value);
            node.averageSum[index] += targetReach * strategy[index];
        }
        return value;
    }

    private Map<String, Double> opponentPolicy(
            int player, String informationSet, List<String> actions) {
        String key = player + ":" + informationSet;
        opponentQueries++;
        queriedOpponentKeys.add(key);
        Map<String, Double> policy = fixedOpponent.at(player, informationSet);
        if (policy == null) {
            missingOpponentQueries++;
            missingOpponentKeys.add(key);
            Map<String, Double> uniform = new LinkedHashMap<>();
            for (String action : actions) uniform.put(action, 1.0 / actions.size());
            return Map.copyOf(uniform);
        }
        if (policy.size() != actions.size())
            throw new IllegalArgumentException("Fixed opponent action set differs from game");
        double sum = 0;
        for (String action : actions) {
            Double probability = policy.get(action);
            if (probability == null || !Double.isFinite(probability) || probability < 0)
                throw new IllegalArgumentException("Invalid fixed opponent action probability");
            sum += probability;
        }
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("Fixed opponent probabilities must sum to one");
        return policy;
    }
}
