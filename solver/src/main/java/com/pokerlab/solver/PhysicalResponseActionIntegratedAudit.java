package com.pokerlab.solver;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Held-out physical-deck response comparison that samples public cards but integrates over every
 * policy action. Each action branch at a given public chance depth shares that depth's random
 * quantile, preserving an unbiased estimate while removing action-sampling noise.
 */
public final class PhysicalResponseActionIntegratedAudit {
    public record Report(
            String gameHash,
            int targetPlayer,
            long seed,
            int privateDealTraversals,
            int independentBatches,
            PhysicalResponseHeldOutAudit.EvaluationMode rootMode,
            double baselineTargetUtilityBb,
            double responseTargetUtilityBb,
            double responseGainBb,
            double pairedStandardErrorBb,
            double baselineFallbackPathProbability,
            double responseFallbackPathProbability) {
        public double approximateGainLower95Bb() {
            return responseGainBb - 1.96 * pairedStandardErrorBb;
        }

        public double approximateGainUpper95Bb() {
            return responseGainBb + 1.96 * pairedStandardErrorBb;
        }
    }

    private record Expected(double utilityBb, double fallbackPathProbability) {}

    private PhysicalResponseActionIntegratedAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution baseline,
            CfrSolution response,
            int target,
            int privateDealTraversals,
            long seed,
            PhysicalResponseHeldOutAudit.EvaluationMode rootMode) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(rootMode, "rootMode");
        if (target != 0 && target != 1)
            throw new IllegalArgumentException("Target player must be 0 or 1");
        if (privateDealTraversals < 2 || privateDealTraversals > 100_000)
            throw new IllegalArgumentException("Expected 2-100000 private-deal traversals");
        List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> rootDeals =
                rootMode == PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT
                        ? game.chanceOutcomes(game.initialState())
                        : null;
        int batchSize = rootDeals == null ? 1 : rootDeals.size();
        if (rootDeals != null) {
            if (batchSize == 0
                    || privateDealTraversals % batchSize != 0
                    || privateDealTraversals / batchSize < 2)
                throw new IllegalArgumentException(
                        "Stratified traversals require at least two complete root-deal batches");
            double totalProbability = 0;
            for (var deal : rootDeals) {
                if (!Double.isFinite(deal.probability()) || deal.probability() < 0)
                    throw new IllegalArgumentException("Invalid root-deal probability");
                totalProbability += deal.probability();
            }
            if (Math.abs(totalProbability - 1) > 1e-9)
                throw new IllegalArgumentException("Root-deal probabilities must sum to one");
        }
        int batches = privateDealTraversals / batchSize;
        var random = new SplittableRandom(seed);
        var baselineUtility = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var responseUtility = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var pairedGain = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var baselineFallback = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var responseFallback = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        for (int batch = 0; batch < batches; batch++) {
            double batchBaseline = 0;
            double batchResponse = 0;
            double batchBaselineFallback = 0;
            double batchResponseFallback = 0;
            for (int index = 0; index < batchSize; index++) {
                var deal =
                        rootDeals == null
                                ? game.sampleChanceOutcome(game.initialState(), random.nextDouble())
                                : rootDeals.get(index);
                double weight = rootDeals == null ? 1 : deal.probability();
                var continuation = new SplittableRandom(random.nextLong());
                double[] publicQuantiles = {
                    continuation.nextDouble(), continuation.nextDouble(), continuation.nextDouble()
                };
                var original =
                        evaluate(
                                game,
                                baseline,
                                response,
                                target,
                                deal.state(),
                                publicQuantiles,
                                0,
                                false,
                                false);
                var candidate =
                        evaluate(
                                game,
                                baseline,
                                response,
                                target,
                                deal.state(),
                                publicQuantiles,
                                0,
                                true,
                                false);
                batchBaseline += weight * original.utilityBb();
                batchResponse += weight * candidate.utilityBb();
                batchBaselineFallback += weight * original.fallbackPathProbability();
                batchResponseFallback += weight * candidate.fallbackPathProbability();
            }
            baselineUtility.add(batchBaseline);
            responseUtility.add(batchResponse);
            pairedGain.add(batchResponse - batchBaseline);
            baselineFallback.add(batchBaselineFallback);
            responseFallback.add(batchResponseFallback);
        }
        return new Report(
                game.contentHash(),
                target,
                seed,
                privateDealTraversals,
                batches,
                rootMode,
                baselineUtility.mean(),
                responseUtility.mean(),
                pairedGain.mean(),
                pairedGain.standardError(),
                baselineFallback.mean(),
                responseFallback.mean());
    }

    private static Expected evaluate(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution baseline,
            CfrSolution response,
            int target,
            ButtonBigBlindPhysicalDeckGame.State state,
            double[] publicQuantiles,
            int chanceDepth,
            boolean useResponse,
            boolean fallbackSeen) {
        if (game.isTerminal(state)) {
            double utility = game.terminalUtility(state);
            return new Expected(target == 0 ? utility : -utility, fallbackSeen ? 1.0 : 0.0);
        }
        int player = game.currentPlayer(state);
        if (player == -1) {
            if (chanceDepth >= publicQuantiles.length)
                throw new IllegalArgumentException("Expected at most flop, turn and river chance");
            var next = game.sampleChanceOutcome(state, publicQuantiles[chanceDepth]);
            return evaluate(
                    game,
                    baseline,
                    response,
                    target,
                    next.state(),
                    publicQuantiles,
                    chanceDepth + 1,
                    useResponse,
                    fallbackSeen);
        }
        var actions = game.legalActions(state);
        String informationSet = game.informationSet(state);
        Map<String, Double> policy = baseline.at(player, informationSet);
        boolean fallbackHere = false;
        if (useResponse && player == target) {
            var replacement = response.at(player, informationSet);
            if (replacement == null) fallbackHere = true;
            else policy = replacement;
        }
        if (policy == null) fallbackHere = true;
        double expectedUtility = 0;
        double expectedFallback = 0;
        double policyMass = 0;
        for (String action : actions) {
            double probability =
                    policy == null ? 1.0 / actions.size() : probability(policy, action);
            policyMass += probability;
            if (probability == 0) continue;
            var child =
                    evaluate(
                            game,
                            baseline,
                            response,
                            target,
                            game.afterAction(state, action),
                            publicQuantiles,
                            chanceDepth,
                            useResponse,
                            fallbackSeen || fallbackHere);
            expectedUtility += probability * child.utilityBb();
            expectedFallback += probability * child.fallbackPathProbability();
        }
        if (policy != null && (policy.size() != actions.size() || Math.abs(policyMass - 1) > 1e-9))
            throw new IllegalArgumentException("Strategy action probabilities differ from game");
        return new Expected(expectedUtility, expectedFallback);
    }

    private static double probability(Map<String, Double> policy, String action) {
        Double value = policy.get(action);
        if (value == null || !Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Invalid action probability");
        return value;
    }
}
