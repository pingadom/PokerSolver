package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Alternating exhaustive or chance-sampled regret matching for bounded multi-player research games.
 * Unlike two-player zero-sum CFR, a small regret value here does not certify a Nash equilibrium.
 */
public final class MultiPlayerCfrSolver<S> {
    public enum ChanceMode {
        EXHAUSTIVE,
        SAMPLED,
        SAMPLED_AFTER_ROOT,
        /** Enumerate chance depths 0 and 1; connected poker uses private deals then flops. */
        SAMPLED_RUNOUTS
    }

    public record Statistics(
            long visitedNodes,
            long terminalNodes,
            long sampledChanceNodes,
            long baselineCorrections) {}

    record Sample<T>(T state, double importanceRatio) {}

    private static final double CHANCE_TOLERANCE = 1e-9;
    private final MultiPlayerCfrGame<S> game;
    private final CfrSolver.Variant variant;
    private final int players;
    private final ChanceMode chanceMode;
    private final long chanceSeed;
    private final double uniformMixture;
    private final boolean chanceBaselineEnabled;
    private final boolean linearWeighting;
    private final List<Double> passChanceDraws = new ArrayList<>();
    private SplittableRandom chanceRandom;
    private long visitedNodes, terminalNodes, sampledChanceNodes, baselineCorrections;
    private final Map<String, InformationSet> informationSets = new LinkedHashMap<>();
    private final Map<String, double[]> iterationStrategies = new LinkedHashMap<>();

    public MultiPlayerCfrSolver(MultiPlayerCfrGame<S> game, CfrSolver.Variant variant) {
        this(game, variant, ChanceMode.EXHAUSTIVE, 0, 0);
    }

    /**
     * Sample chance with proposal q=(1-mixture)*p+mixture/N. Every player action is still
     * traversed; sampled prefix and suffix likelihood ratios correct regrets, averages and returned
     * values.
     */
    public MultiPlayerCfrSolver(
            MultiPlayerCfrGame<S> game,
            CfrSolver.Variant variant,
            ChanceMode chanceMode,
            long chanceSeed,
            double uniformMixture) {
        this(game, variant, chanceMode, chanceSeed, uniformMixture, true);
    }

    public MultiPlayerCfrSolver(
            MultiPlayerCfrGame<S> game,
            CfrSolver.Variant variant,
            ChanceMode chanceMode,
            long chanceSeed,
            double uniformMixture,
            boolean chanceBaselineEnabled) {
        this(game, variant, chanceMode, chanceSeed, uniformMixture, chanceBaselineEnabled, false);
    }

    /** Linear weighting applies to BOTH cumulative regret updates and strategy averaging. */
    public MultiPlayerCfrSolver(
            MultiPlayerCfrGame<S> game,
            CfrSolver.Variant variant,
            ChanceMode chanceMode,
            long chanceSeed,
            double uniformMixture,
            boolean chanceBaselineEnabled,
            boolean linearWeighting) {
        this.game = Objects.requireNonNull(game, "game");
        this.variant = Objects.requireNonNull(variant, "variant");
        this.chanceMode = Objects.requireNonNull(chanceMode, "chanceMode");
        this.chanceSeed = chanceSeed;
        if (!Double.isFinite(uniformMixture) || uniformMixture < 0 || uniformMixture > 0.95)
            throw new IllegalArgumentException("Uniform proposal mixture must be in [0, 0.95]");
        if (chanceMode == ChanceMode.EXHAUSTIVE && uniformMixture != 0)
            throw new IllegalArgumentException("Exhaustive traversal has no sampling proposal");
        if (chanceMode != ChanceMode.EXHAUSTIVE && variant != CfrSolver.Variant.VANILLA)
            throw new IllegalArgumentException("Chance sampling supports vanilla CFR only");
        this.uniformMixture = uniformMixture;
        this.chanceBaselineEnabled = chanceBaselineEnabled;
        if (linearWeighting && variant != CfrSolver.Variant.VANILLA)
            throw new IllegalArgumentException(
                    "Linear weighting requires unclipped vanilla regret matching");
        this.linearWeighting = linearWeighting;
        this.players = game.playerCount();
        if (players < 2 || players > 6)
            throw new IllegalArgumentException("Expected two to six players");
    }

    public CfrSolution solve(int iterations) {
        if (iterations < 1) throw new IllegalArgumentException("iterations must be positive");
        informationSets.clear();
        chanceRandom = new SplittableRandom(chanceSeed);
        visitedNodes = terminalNodes = sampledChanceNodes = baselineCorrections = 0;
        for (int iteration = 1; iteration <= iterations; iteration++) {
            for (int target = 0; target < players; target++) {
                iterationStrategies.clear();
                // A fresh world for each alternating pass is independent of earlier players'
                // updates. Within a pass, depth quantiles are shared across action branches only.
                passChanceDraws.clear();
                double[] reach = new double[players];
                Arrays.fill(reach, 1);
                traverse(game.initialState(), reach, 1, target, iteration, 0);
                if (variant == CfrSolver.Variant.CFR_PLUS)
                    informationSets.values().forEach(InformationSet::clipNegativeRegrets);
            }
        }
        Map<String, Map<String, Double>> average = new LinkedHashMap<>();
        informationSets.forEach((key, node) -> average.put(key, node.averageStrategy()));
        return new CfrSolution(iterations, Map.copyOf(average));
    }

    public Statistics statistics() {
        return new Statistics(visitedNodes, terminalNodes, sampledChanceNodes, baselineCorrections);
    }

    private double traverse(
            S state,
            double[] reach,
            double chanceReach,
            int target,
            int iterationWeight,
            int chanceDepth) {
        visitedNodes++;
        if (game.isTerminal(state)) {
            terminalNodes++;
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
            if (chanceMode == ChanceMode.SAMPLED
                    || chanceMode == ChanceMode.SAMPLED_AFTER_ROOT && chanceDepth > 0
                    || chanceMode == ChanceMode.SAMPLED_RUNOUTS && chanceDepth > 1) {
                sampledChanceNodes++;
                while (passChanceDraws.size() <= chanceDepth)
                    passChanceDraws.add(chanceRandom.nextDouble());
                var sampled = sample(outcomes, passChanceDraws.get(chanceDepth), uniformMixture);
                double continuation =
                        traverse(
                                sampled.state(),
                                reach,
                                chanceReach * sampled.importanceRatio(),
                                target,
                                iterationWeight,
                                chanceDepth + 1);
                if (sampled.importanceRatio() == 1) return continuation;
                double baseline =
                        chanceBaselineEnabled ? game.chanceBaselineUtility(state, target) : 0;
                if (!Double.isFinite(baseline))
                    throw new IllegalArgumentException("Non-finite chance baseline");
                if (baseline != 0) baselineCorrections++;
                return baseline + sampled.importanceRatio() * (continuation - baseline);
            }
            double utility = 0;
            for (ChanceOutcome<S> outcome : outcomes)
                utility +=
                        outcome.probability()
                                * traverse(
                                        outcome.state(),
                                        reach,
                                        chanceReach * outcome.probability(),
                                        target,
                                        iterationWeight,
                                        chanceDepth + 1);
            return utility;
        }
        if (player < 0 || player >= players)
            throw new IllegalArgumentException("Invalid player index");
        List<String> actions = List.copyOf(game.legalActions(state));
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
                            iterationWeight,
                            chanceDepth);
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
                    variant == CfrSolver.Variant.CFR_PLUS || linearWeighting ? iterationWeight : 1,
                    linearWeighting ? iterationWeight : 1);
        }
        return nodeUtility;
    }

    /** Package-visible for exact proposal/importance accounting tests. */
    static <T> Sample<T> sample(List<ChanceOutcome<T>> outcomes, double quantile, double mixture) {
        if (outcomes.isEmpty()
                || !Double.isFinite(quantile)
                || quantile < 0
                || quantile >= 1
                || !Double.isFinite(mixture)
                || mixture < 0
                || mixture > 0.95)
            throw new IllegalArgumentException("Invalid chance proposal request");
        double sum = outcomes.stream().mapToDouble(ChanceOutcome::probability).sum();
        if (Math.abs(sum - 1) > CHANCE_TOLERANCE)
            throw new IllegalArgumentException("Chance probabilities must sum to one");
        double cumulative = 0;
        for (int i = 0; i < outcomes.size(); i++) {
            var outcome = outcomes.get(i);
            double proposal = (1 - mixture) * outcome.probability() + mixture / outcomes.size();
            cumulative += proposal;
            if (quantile < cumulative || i == outcomes.size() - 1)
                return new Sample<>(outcome.state(), outcome.probability() / proposal);
        }
        throw new IllegalStateException("Missing proposal outcome");
    }

    private static final class InformationSet {
        private final List<String> actions;
        private final double[] regret;
        private final double[] strategySum;

        private InformationSet(List<String> actions) {
            CfrSolver.validateActions(actions);
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
                int averagingWeight,
                int regretWeight) {
            for (int index = 0; index < strategy.length; index++) {
                regret[index] +=
                        counterfactualReach * regretWeight * (actionUtilities[index] - nodeUtility);
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
