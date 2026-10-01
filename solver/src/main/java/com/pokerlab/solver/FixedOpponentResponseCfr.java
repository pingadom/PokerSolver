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
    public enum ChanceMode {
        SAMPLED_ALL,
        EXACT_ROOT
    }

    public record Result(
            CfrSolution response,
            CfrSolution finalRegretPolicy,
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

        Map<String, Double> finalRegretStrategy() {
            double[] probabilities = currentStrategy();
            Map<String, Double> result = new LinkedHashMap<>();
            for (int index = 0; index < actions.size(); index++)
                result.put(actions.get(index), probabilities[index]);
            return Map.copyOf(result);
        }
    }

    private final CfrGame<S> game;
    private final CfrSolution fixedOpponent;
    private final int target;
    private final long seed;
    private final ChanceMode chanceMode;
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
        this(game, fixedOpponent, target, seed, ChanceMode.SAMPLED_ALL);
    }

    public FixedOpponentResponseCfr(
            CfrGame<S> game,
            CfrSolution fixedOpponent,
            int target,
            long seed,
            ChanceMode chanceMode) {
        this.game = Objects.requireNonNull(game, "game");
        this.fixedOpponent = Objects.requireNonNull(fixedOpponent, "fixedOpponent");
        if (target != 0 && target != 1)
            throw new IllegalArgumentException("Target player must be 0 or 1");
        this.target = target;
        this.seed = seed;
        this.chanceMode = Objects.requireNonNull(chanceMode, "chanceMode");
    }

    public Result solve(int iterations) {
        return solveCheckpoints(List.of(iterations)).getFirst();
    }

    /**
     * Captures immutable policies at increasing iteration budgets on one seeded training stream.
     * Each checkpoint equals a standalone solve at that budget without repeating earlier
     * traversals.
     */
    public List<Result> solveCheckpoints(List<Integer> checkpoints) {
        Objects.requireNonNull(checkpoints, "checkpoints");
        if (checkpoints.isEmpty())
            throw new IllegalArgumentException("Expected response checkpoints");
        int previous = 0;
        for (Integer checkpoint : checkpoints) {
            if (checkpoint == null || checkpoint <= previous || checkpoint > 1_000_000)
                throw new IllegalArgumentException(
                        "Response checkpoints must increase strictly within 1-1000000");
            previous = checkpoint;
        }
        nodes.clear();
        queriedOpponentKeys.clear();
        missingOpponentKeys.clear();
        opponentQueries = 0;
        missingOpponentQueries = 0;
        random = new SplittableRandom(seed);
        List<ChanceOutcome<S>> rootOutcomes = null;
        if (chanceMode == ChanceMode.EXACT_ROOT) {
            S root = game.initialState();
            if (game.currentPlayer(root) != -1)
                throw new IllegalArgumentException("Exact-root mode requires root chance");
            rootOutcomes = List.copyOf(game.chanceOutcomes(root));
            if (rootOutcomes.isEmpty())
                throw new IllegalArgumentException("Exact-root mode requires root outcomes");
            double sum = 0;
            for (var outcome : rootOutcomes) {
                if (!Double.isFinite(outcome.probability()) || outcome.probability() < 0)
                    throw new IllegalArgumentException("Invalid root chance probability");
                sum += outcome.probability();
            }
            if (Math.abs(sum - 1) > 1e-9)
                throw new IllegalArgumentException("Root chance probabilities must sum to one");
        }
        List<Result> results = new ArrayList<>(checkpoints.size());
        int checkpointIndex = 0;
        for (int iteration = 1; iteration <= checkpoints.getLast(); iteration++) {
            chanceDraws.clear();
            iterationStrategies.clear();
            if (rootOutcomes == null) {
                traverse(game.initialState(), 1, 1, 1, 0);
            } else {
                for (var outcome : rootOutcomes)
                    if (outcome.probability() > 0)
                        traverse(outcome.state(), 1, 1, outcome.probability(), 1);
            }
            if (iteration == checkpoints.get(checkpointIndex)) {
                results.add(snapshot(iteration));
                checkpointIndex++;
            }
        }
        return List.copyOf(results);
    }

    private Result snapshot(int iterations) {
        Map<String, Map<String, Double>> average = new LinkedHashMap<>();
        nodes.forEach((key, node) -> average.put(key, node.averageStrategy()));
        Map<String, Map<String, Double>> finalRegret = new LinkedHashMap<>();
        nodes.forEach((key, node) -> finalRegret.put(key, node.finalRegretStrategy()));
        return new Result(
                new CfrSolution(iterations, average),
                new CfrSolution(iterations, finalRegret),
                opponentQueries,
                missingOpponentQueries,
                queriedOpponentKeys.size(),
                missingOpponentKeys.size());
    }

    private double traverse(
            S state,
            double targetReach,
            double opponentReach,
            double chanceReach,
            int chanceDepth) {
        if (game.isTerminal(state)) {
            double utility = game.terminalUtility(state);
            if (!Double.isFinite(utility)) throw new IllegalArgumentException("Non-finite payoff");
            return target == 0 ? utility : -utility;
        }
        int player = game.currentPlayer(state);
        if (player == -1) {
            while (chanceDraws.size() <= chanceDepth) chanceDraws.add(random.nextDouble());
            var outcome = game.sampleChanceOutcome(state, chanceDraws.get(chanceDepth));
            return traverse(
                    outcome.state(), targetReach, opponentReach, chanceReach, chanceDepth + 1);
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
                                            chanceReach,
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
                            chanceReach,
                            chanceDepth);
            value += strategy[index] * values[index];
        }
        for (int index = 0; index < actions.size(); index++) {
            node.regrets[index] += opponentReach * chanceReach * (values[index] - value);
            node.averageSum[index] += targetReach * chanceReach * strategy[index];
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
