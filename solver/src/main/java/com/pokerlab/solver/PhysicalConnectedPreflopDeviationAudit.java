package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Estimates a one-decision preflop deviation while keeping the learned continuation fixed. It is
 * only a diagnostic: postflop deviations and a full best response are outside this audit.
 */
public final class PhysicalConnectedPreflopDeviationAudit {
    public record Decision(
            String player,
            String ownHand,
            double reachWeight,
            double foldUtilityBb,
            double continueUtilityBb,
            double continueStandardErrorBb,
            double policyContinueProbability,
            double estimatedImprovementBb) {}

    public record Report(String gameHash, long seed, int trialsPerDeal, List<Decision> decisions) {}

    private record WeightedDeal(ButtonBigBlindPhysicalDeckGame.State state, double weight) {}

    private record Estimate(double mean, double standardError) {}

    private PhysicalConnectedPreflopDeviationAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            int trialsPerDeal,
            long seed) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(solution, "solution");
        if (trialsPerDeal < 2 || trialsPerDeal > 100_000)
            throw new IllegalArgumentException("Expected 2-100000 trials per private deal");
        Map<String, List<WeightedDeal>> buttonHands = new LinkedHashMap<>();
        Map<String, List<WeightedDeal>> bigBlindHands = new LinkedHashMap<>();
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var state = outcome.state();
            buttonHands
                    .computeIfAbsent(state.button().key(), ignored -> new ArrayList<>())
                    .add(new WeightedDeal(state, outcome.probability()));
            var afterOpen = game.afterAction(state, "open3");
            double buttonOpen =
                    requiredPolicy(solution, 1, game.informationSet(state), "open3", "fold");
            bigBlindHands
                    .computeIfAbsent(state.bigBlind().key(), ignored -> new ArrayList<>())
                    .add(new WeightedDeal(afterOpen, outcome.probability() * buttonOpen));
        }
        SplittableRandom random = new SplittableRandom(seed);
        List<Decision> decisions = new ArrayList<>();
        collect(game, solution, buttonHands, true, trialsPerDeal, random, decisions);
        collect(game, solution, bigBlindHands, false, trialsPerDeal, random, decisions);
        return new Report(game.contentHash(), seed, trialsPerDeal, List.copyOf(decisions));
    }

    private static void collect(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            Map<String, List<WeightedDeal>> groups,
            boolean button,
            int trials,
            SplittableRandom random,
            List<Decision> decisions) {
        double totalReach =
                groups.values().stream()
                        .flatMap(List::stream)
                        .mapToDouble(WeightedDeal::weight)
                        .sum();
        if (totalReach == 0) return;
        for (var entry : groups.entrySet()) {
            List<WeightedDeal> deals = entry.getValue();
            double groupReach = deals.stream().mapToDouble(WeightedDeal::weight).sum();
            if (groupReach == 0) continue;
            double foldUtility = 0;
            double continueUtility = 0;
            double continueVariance = 0;
            for (WeightedDeal deal : deals) {
                double probability = deal.weight() / groupReach;
                var folded = game.afterAction(deal.state(), "fold");
                foldUtility += probability * perspective(game.terminalUtility(folded), button);
                var continued = game.afterAction(deal.state(), button ? "open3" : "call");
                Estimate estimate =
                        sampleContinuation(game, solution, continued, button, trials, random);
                continueUtility += probability * estimate.mean();
                continueVariance +=
                        probability
                                * probability
                                * estimate.standardError()
                                * estimate.standardError();
            }
            int player = button ? 1 : 0;
            String riskAction = button ? "open3" : "call";
            double policyContinue =
                    requiredPolicy(
                            solution,
                            player,
                            game.informationSet(deals.getFirst().state()),
                            riskAction,
                            "fold");
            double policyValue =
                    policyContinue * continueUtility + (1 - policyContinue) * foldUtility;
            decisions.add(
                    new Decision(
                            button ? "BTN" : "BB",
                            entry.getKey(),
                            groupReach / totalReach,
                            foldUtility,
                            continueUtility,
                            Math.sqrt(continueVariance),
                            policyContinue,
                            Math.max(foldUtility, continueUtility) - policyValue));
        }
    }

    private static Estimate sampleContinuation(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution solution,
            ButtonBigBlindPhysicalDeckGame.State startingState,
            boolean button,
            int trials,
            SplittableRandom random) {
        double mean = 0;
        double sumSquaredDifferences = 0;
        for (int trial = 1; trial <= trials; trial++) {
            var state = startingState;
            while (!game.isTerminal(state)) {
                int player = game.currentPlayer(state);
                if (player == -1) {
                    state = game.sampleChanceOutcome(state, random.nextDouble()).state();
                } else {
                    List<String> actions = game.legalActions(state);
                    Map<String, Double> policy = solution.at(player, game.informationSet(state));
                    state =
                            game.afterAction(
                                    state,
                                    PhysicalConnectedStrategyAudit.drawAction(
                                            actions, policy, random.nextDouble()));
                }
            }
            double value = perspective(game.terminalUtility(state), button);
            double delta = value - mean;
            mean += delta / trial;
            sumSquaredDifferences += delta * (value - mean);
        }
        return new Estimate(mean, Math.sqrt(sumSquaredDifferences / (trials - 1) / trials));
    }

    private static double requiredPolicy(
            CfrSolution solution,
            int player,
            String informationSet,
            String continueAction,
            String foldAction) {
        Map<String, Double> policy = solution.at(player, informationSet);
        if (policy == null || policy.size() != 2)
            throw new IllegalArgumentException("Missing preflop policy");
        Double continued = policy.get(continueAction);
        Double folded = policy.get(foldAction);
        if (continued == null
                || folded == null
                || !Double.isFinite(continued)
                || !Double.isFinite(folded)
                || continued < 0
                || folded < 0
                || Math.abs(continued + folded - 1) > 1e-9)
            throw new IllegalArgumentException("Invalid preflop policy");
        return continued;
    }

    private static double perspective(double bigBlindUtility, boolean button) {
        return button ? -bigBlindUtility : bigBlindUtility;
    }
}
