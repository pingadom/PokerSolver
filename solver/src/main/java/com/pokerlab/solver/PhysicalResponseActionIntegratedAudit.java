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
            double responseFallbackPathProbability,
            double responseMissingPathProbability,
            double terminalUtilitySpanBb,
            double completionLowerStandardErrorBb,
            double completionUpperStandardErrorBb) {
        public double approximateGainLower95Bb() {
            return responseGainBb - 1.96 * pairedStandardErrorBb;
        }

        public double approximateGainUpper95Bb() {
            return responseGainBb + 1.96 * pairedStandardErrorBb;
        }

        /** Sample estimate of the lower completion-only envelope. */
        public double completionGainLowerBb() {
            return responseGainBb - terminalUtilitySpanBb * responseMissingPathProbability;
        }

        /** Sample estimate of the upper completion-only envelope. */
        public double completionGainUpperBb() {
            return responseGainBb + terminalUtilitySpanBb * responseMissingPathProbability;
        }

        /** Nominal one-sided sampling limits, not a full-deck best-response guarantee. */
        public double approximateCompletionGainLower95Bb() {
            return completionGainLowerBb() - 1.645 * completionLowerStandardErrorBb;
        }

        public double approximateCompletionGainUpper95Bb() {
            return completionGainUpperBb() + 1.645 * completionUpperStandardErrorBb;
        }

        /** One-sided Hoeffding limit for independent bounded batches; deliberately conservative. */
        public double boundedCompletionGainUpper95Bb() {
            return completionGainUpperBb()
                    + 3
                            * terminalUtilitySpanBb
                            * Math.sqrt(Math.log(20) / (2.0 * independentBatches));
        }
    }

    private record Expected(
            double utilityBb,
            double fallbackPathProbability,
            double responseMissingPathProbability) {}

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
        var responseMissing = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var completionLower = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var completionUpper = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        double terminalUtilitySpan = 2 * game.maximumAbsoluteTerminalUtilityBb();
        for (int batch = 0; batch < batches; batch++) {
            double batchBaseline = 0;
            double batchResponse = 0;
            double batchBaselineFallback = 0;
            double batchResponseFallback = 0;
            double batchResponseMissing = 0;
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
                                false,
                                false);
                batchBaseline += weight * original.utilityBb();
                batchResponse += weight * candidate.utilityBb();
                batchBaselineFallback += weight * original.fallbackPathProbability();
                batchResponseFallback += weight * candidate.fallbackPathProbability();
                batchResponseMissing += weight * candidate.responseMissingPathProbability();
            }
            baselineUtility.add(batchBaseline);
            responseUtility.add(batchResponse);
            double batchGain = batchResponse - batchBaseline;
            pairedGain.add(batchGain);
            baselineFallback.add(batchBaselineFallback);
            responseFallback.add(batchResponseFallback);
            responseMissing.add(batchResponseMissing);
            completionLower.add(batchGain - terminalUtilitySpan * batchResponseMissing);
            completionUpper.add(batchGain + terminalUtilitySpan * batchResponseMissing);
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
                responseFallback.mean(),
                responseMissing.mean(),
                terminalUtilitySpan,
                completionLower.standardError(),
                completionUpper.standardError());
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
            boolean fallbackSeen,
            boolean responseMissingSeen) {
        if (game.isTerminal(state)) {
            double utility = game.terminalUtility(state);
            return new Expected(
                    target == 0 ? utility : -utility,
                    fallbackSeen ? 1.0 : 0.0,
                    responseMissingSeen ? 1.0 : 0.0);
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
                    fallbackSeen,
                    responseMissingSeen);
        }
        var actions = game.legalActions(state);
        String informationSet = game.informationSet(state);
        Map<String, Double> policy = baseline.at(player, informationSet);
        boolean fallbackHere = false;
        boolean responseMissingHere = false;
        if (useResponse && player == target) {
            var replacement = response.at(player, informationSet);
            if (replacement == null) {
                fallbackHere = true;
                responseMissingHere = true;
            } else policy = replacement;
        }
        if (policy == null) fallbackHere = true;
        double expectedUtility = 0;
        double expectedFallback = 0;
        double expectedResponseMissing = 0;
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
                            fallbackSeen || fallbackHere,
                            responseMissingSeen || responseMissingHere);
            expectedUtility += probability * child.utilityBb();
            expectedFallback += probability * child.fallbackPathProbability();
            expectedResponseMissing += probability * child.responseMissingPathProbability();
        }
        if (policy != null && (policy.size() != actions.size() || Math.abs(policyMass - 1) > 1e-9))
            throw new IllegalArgumentException("Strategy action probabilities differ from game");
        return new Expected(expectedUtility, expectedFallback, expectedResponseMissing);
    }

    private static double probability(Map<String, Double> policy, String action) {
        Double value = policy.get(action);
        if (value == null || !Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Invalid action probability");
        return value;
    }
}
