package com.pokerlab.solver;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/** Held-out physical-runout self-play audit for a sparse, research-only connected strategy. */
public final class PhysicalConnectedStrategyAudit {
    public record Report(
            String gameHash,
            long seed,
            int trials,
            double meanBigBlindBb,
            double samplingStandardErrorBb,
            long decisions,
            long missingStrategyDecisions) {
        public double missingStrategyRate() {
            return decisions == 0 ? 0 : (double) missingStrategyDecisions / decisions;
        }
    }

    private PhysicalConnectedStrategyAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game, CfrSolution solution, int trials, long seed) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        if (trials < 2 || trials > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 held-out runouts");
        SplittableRandom random = new SplittableRandom(seed);
        long decisions = 0;
        long missing = 0;
        double mean = 0;
        double sumSquaredDifferences = 0;
        for (int trial = 1; trial <= trials; trial++) {
            var state = game.initialState();
            while (!game.isTerminal(state)) {
                int player = game.currentPlayer(state);
                if (player == -1) {
                    state = game.sampleChanceOutcome(state, random.nextDouble()).state();
                    continue;
                }
                decisions++;
                List<String> actions = game.legalActions(state);
                Map<String, Double> policy = solution.at(player, game.informationSet(state));
                if (policy == null) missing++;
                state = game.afterAction(state, drawAction(actions, policy, random.nextDouble()));
            }
            double value = game.terminalUtility(state);
            double delta = value - mean;
            mean += delta / trial;
            sumSquaredDifferences += delta * (value - mean);
        }
        return new Report(
                game.contentHash(),
                seed,
                trials,
                mean,
                Math.sqrt(sumSquaredDifferences / (trials - 1) / trials),
                decisions,
                missing);
    }

    static String drawAction(List<String> actions, Map<String, Double> policy, double quantile) {
        if (policy == null) return actions.get((int) (quantile * actions.size()));
        if (policy.size() != actions.size())
            throw new IllegalArgumentException("Strategy action set differs from the game");
        double sum = 0;
        for (String action : actions) {
            Double probability = policy.get(action);
            if (probability == null || !Double.isFinite(probability) || probability < 0)
                throw new IllegalArgumentException("Invalid action probability");
            sum += probability;
        }
        if (Math.abs(sum - 1) > 1e-9)
            throw new IllegalArgumentException("Strategy probabilities must sum to one");
        double cumulative = 0;
        for (String action : actions) {
            cumulative += policy.get(action);
            if (quantile < cumulative) return action;
        }
        return actions.getLast();
    }
}
