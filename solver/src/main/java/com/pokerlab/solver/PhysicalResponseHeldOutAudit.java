package com.pokerlab.solver;

import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Paired, held-out full-physical-deck comparison of one response policy against the baseline.
 * Missing response keys use the baseline; missing baseline keys use explicit uniform actions.
 */
public final class PhysicalResponseHeldOutAudit {
    public enum EvaluationMode {
        SAMPLED_ROOT,
        STRATIFIED_ROOT
    }

    public record Report(
            String gameHash,
            int targetPlayer,
            long seed,
            int trials,
            double baselineTargetUtilityBb,
            double responseTargetUtilityBb,
            double responseGainBb,
            double pairedStandardErrorBb,
            long baselineMissingActionDecisions,
            long responseMissingBaselineDecisions,
            long responseFallbackToBaselineDecisions,
            int baselineFallbackTrajectories,
            int responseFallbackTrajectories,
            EvaluationMode evaluationMode,
            int independentBatches) {
        public double approximateGainLower95Bb() {
            return responseGainBb - 1.96 * pairedStandardErrorBb;
        }

        public double approximateGainUpper95Bb() {
            return responseGainBb + 1.96 * pairedStandardErrorBb;
        }
    }

    private record Rollout(
            double targetUtilityBb,
            long missingBaselineDecisions,
            long responseFallbackDecisions) {}

    private PhysicalResponseHeldOutAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution baseline,
            CfrSolution response,
            int target,
            int trials,
            long seed) {
        return assess(game, baseline, response, target, trials, seed, EvaluationMode.SAMPLED_ROOT);
    }

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution baseline,
            CfrSolution response,
            int target,
            int trials,
            long seed,
            EvaluationMode mode) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(baseline, "baseline");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(mode, "mode");
        if (target != 0 && target != 1)
            throw new IllegalArgumentException("Target player must be 0 or 1");
        if (trials < 2 || trials > 1_000_000)
            throw new IllegalArgumentException("Expected 2-1000000 held-out trials");
        var rootDeals =
                mode == EvaluationMode.STRATIFIED_ROOT
                        ? game.chanceOutcomes(game.initialState())
                        : null;
        int batchSize = rootDeals == null ? 1 : rootDeals.size();
        if (rootDeals != null) {
            if (batchSize == 0 || trials % batchSize != 0 || trials / batchSize < 2)
                throw new IllegalArgumentException(
                        "Stratified trials must contain at least two complete root-deal batches");
            double mass = 0;
            for (var deal : rootDeals) {
                if (!Double.isFinite(deal.probability()) || deal.probability() < 0)
                    throw new IllegalArgumentException("Invalid root-deal probability");
                mass += deal.probability();
            }
            if (Math.abs(mass - 1) > 1e-9)
                throw new IllegalArgumentException("Root-deal probabilities must sum to one");
        }
        int batches = trials / batchSize;
        var random = new SplittableRandom(seed);
        var baselineUtility = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var responseUtility = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        var pairedGain = new PhysicalRiverHeldOutDecisionAudit.SampleMoments();
        long baselineMissing = 0;
        long responseMissing = 0;
        long responseFallback = 0;
        int baselineFallbackPaths = 0;
        int responseFallbackPaths = 0;
        for (int batch = 0; batch < batches; batch++) {
            double batchBaseline = 0;
            double batchResponse = 0;
            for (int index = 0; index < batchSize; index++) {
                var deal =
                        rootDeals == null
                                ? game.sampleChanceOutcome(game.initialState(), random.nextDouble())
                                : rootDeals.get(index);
                double weight = rootDeals == null ? 1 : deal.probability();
                long continuationSeed = random.nextLong();
                var original =
                        rollout(
                                game,
                                baseline,
                                response,
                                target,
                                deal.state(),
                                continuationSeed,
                                false);
                var candidate =
                        rollout(
                                game,
                                baseline,
                                response,
                                target,
                                deal.state(),
                                continuationSeed,
                                true);
                batchBaseline += weight * original.targetUtilityBb();
                batchResponse += weight * candidate.targetUtilityBb();
                baselineMissing += original.missingBaselineDecisions();
                responseMissing += candidate.missingBaselineDecisions();
                responseFallback += candidate.responseFallbackDecisions();
                if (original.missingBaselineDecisions() > 0) baselineFallbackPaths++;
                if (candidate.missingBaselineDecisions() > 0
                        || candidate.responseFallbackDecisions() > 0) responseFallbackPaths++;
            }
            baselineUtility.add(batchBaseline);
            responseUtility.add(batchResponse);
            pairedGain.add(batchResponse - batchBaseline);
        }
        return new Report(
                game.contentHash(),
                target,
                seed,
                trials,
                baselineUtility.mean(),
                responseUtility.mean(),
                pairedGain.mean(),
                pairedGain.standardError(),
                baselineMissing,
                responseMissing,
                responseFallback,
                baselineFallbackPaths,
                responseFallbackPaths,
                mode,
                batches);
    }

    private static Rollout rollout(
            ButtonBigBlindPhysicalDeckGame game,
            CfrSolution baseline,
            CfrSolution response,
            int target,
            ButtonBigBlindPhysicalDeckGame.State state,
            long seed,
            boolean useResponse) {
        var random = new SplittableRandom(seed);
        long missingBaseline = 0;
        long responseFallback = 0;
        while (!game.isTerminal(state)) {
            int player = game.currentPlayer(state);
            if (player == -1) {
                state = game.sampleChanceOutcome(state, random.nextDouble()).state();
                continue;
            }
            var actions = game.legalActions(state);
            String informationSet = game.informationSet(state);
            var policy = baseline.at(player, informationSet);
            if (useResponse && player == target) {
                var replacement = response.at(player, informationSet);
                if (replacement == null) responseFallback++;
                else policy = replacement;
            }
            if (policy == null) missingBaseline++;
            state =
                    game.afterAction(
                            state,
                            PhysicalConnectedStrategyAudit.drawAction(
                                    actions, policy, random.nextDouble()));
        }
        double utility = game.terminalUtility(state);
        return new Rollout(target == 0 ? utility : -utility, missingBaseline, responseFallback);
    }
}
