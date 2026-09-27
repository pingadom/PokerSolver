package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Compares sampled physical-deck check-down rollouts with exact preflop equity. This verifies a
 * fixed-policy chance model, not the sparse CFR profile's strategic quality.
 */
public final class PhysicalConnectedChanceAudit {
    public record Matchup(
            String bigBlindCombo,
            String buttonCombo,
            double dealProbability,
            double exactCheckdownBb,
            double sampledCheckdownBb,
            double samplingStandardErrorBb) {}

    public record Report(
            String gameHash,
            long seed,
            int trialsPerDeal,
            double exactWeightedBb,
            double sampledWeightedBb,
            double signedErrorBb,
            double weightedSamplingStandardErrorBb,
            double maxAbsoluteDealErrorBb,
            List<Matchup> matchups) {}

    private PhysicalConnectedChanceAudit() {}

    public static Report assess(
            ButtonBigBlindPhysicalDeckGame game,
            int trialsPerDeal,
            long seed,
            ExactPreflopEquityOracle oracle) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(oracle, "oracle");
        if (trialsPerDeal < 2 || trialsPerDeal > 1_000_000)
            throw new IllegalArgumentException("Check-down audit needs 2-1000000 trials per deal");
        SplittableRandom random = new SplittableRandom(seed);
        List<Matchup> matchups = new ArrayList<>();
        double exactWeighted = 0;
        double sampledWeighted = 0;
        double weightedVariance = 0;
        double largestError = 0;
        for (var deal : game.chanceOutcomes(game.initialState())) {
            var state = deal.state();
            EquityEstimate equity = oracle.estimate(state.bigBlind(), state.button());
            if (equity.standardError() != 0
                    || equity.trials() != ExactPreflopEquityOracle.BOARD_RUNOUTS)
                throw new IllegalStateException("Expected exact physical-deck matchup equity");
            double exact = equity.equity() * game.potBb() - game.potBb() / 2;
            double mean = 0;
            double sumSquaredDifferences = 0;
            for (int trial = 1; trial <= trialsPerDeal; trial++) {
                double value = checkDown(game, state, random);
                double delta = value - mean;
                mean += delta / trial;
                sumSquaredDifferences += delta * (value - mean);
            }
            double standardError =
                    Math.sqrt(sumSquaredDifferences / (trialsPerDeal - 1) / trialsPerDeal);
            double weight = deal.probability();
            matchups.add(
                    new Matchup(
                            state.bigBlind().key(),
                            state.button().key(),
                            weight,
                            exact,
                            mean,
                            standardError));
            exactWeighted += weight * exact;
            sampledWeighted += weight * mean;
            weightedVariance += weight * weight * standardError * standardError;
            largestError = Math.max(largestError, Math.abs(mean - exact));
        }
        return new Report(
                game.contentHash(),
                seed,
                trialsPerDeal,
                exactWeighted,
                sampledWeighted,
                sampledWeighted - exactWeighted,
                Math.sqrt(weightedVariance),
                largestError,
                List.copyOf(matchups));
    }

    private static double checkDown(
            ButtonBigBlindPhysicalDeckGame game,
            ButtonBigBlindPhysicalDeckGame.State deal,
            SplittableRandom random) {
        var called = game.afterAction(game.afterAction(deal, "open3"), "call");
        var flop = game.sampleChanceOutcome(called, random.nextDouble()).state();
        var turnNode = game.afterAction(game.afterAction(flop, "k"), "k");
        var turn = game.sampleChanceOutcome(turnNode, random.nextDouble()).state();
        var riverNode = game.afterAction(game.afterAction(turn, "k"), "k");
        var river = game.sampleChanceOutcome(riverNode, random.nextDouble()).state();
        return game.terminalUtility(game.afterAction(game.afterAction(river, "k"), "k"));
    }
}
