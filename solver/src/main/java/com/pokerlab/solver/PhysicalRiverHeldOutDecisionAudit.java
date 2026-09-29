package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Sample-split river check/bet decision under two fixed BTN response models. Decisions are chosen
 * using discovery boards only, then scored on independently sampled held-out boards. A bucket below
 * the support threshold chooses check. This does not evaluate equilibrium.
 */
public final class PhysicalRiverHeldOutDecisionAudit {
    public enum ResponseModel {
        HAND_INDEPENDENT_CALL,
        PAIR_OR_BETTER_CALL;

        public static ResponseModel parse(String label) {
            return switch (label) {
                case "independent" -> HAND_INDEPENDENT_CALL;
                case "pair" -> PAIR_OR_BETTER_CALL;
                default ->
                        throw new IllegalArgumentException("Response must be independent or pair");
            };
        }
    }

    public record ModeResult(
            int discoveredBuckets,
            int heldOutSupportedBoards,
            int heldOutBoards,
            double selectedGainBb,
            double physicalOracleGainBb,
            double regretStandardErrorBb) {
        public double supportRate() {
            return heldOutBoards == 0 ? 0 : (double) heldOutSupportedBoards / heldOutBoards;
        }

        public double regretBb() {
            return physicalOracleGainBb - selectedGainBb;
        }

        /** Conditional normal approximation for held-out board sampling only. */
        public double approximateRegretUpper95Bb() {
            return regretBb() + 1.96 * regretStandardErrorBb;
        }
    }

    /** Paired comparison: both bucket policies are scored on the same held-out physical boards. */
    public record PairedGainComparison(double equityMinusCoarseBb, double standardErrorBb) {
        public double approximateLower95Bb() {
            return equityMinusCoarseBb - 1.96 * standardErrorBb;
        }

        public double approximateUpper95Bb() {
            return equityMinusCoarseBb + 1.96 * standardErrorBb;
        }
    }

    public record Report(
            ButtonBigBlindRangeValidationFixture.RangeProfile rangeProfile,
            long seed,
            int sampledBoards,
            int minimumDiscoveryBoards,
            double buttonCallProbability,
            ResponseModel responseModel,
            ModeResult fine,
            ModeResult texture,
            ModeResult coarse,
            ModeResult equity,
            PairedGainComparison equityVersusCoarse) {}

    static final class SampleMoments {
        private int count;
        private double mean;
        private double squaredDeviationSum;

        void add(double value) {
            count++;
            double offset = value - mean;
            mean += offset / count;
            squaredDeviationSum += offset * (value - mean);
        }

        double mean() {
            return mean;
        }

        double standardError() {
            return count < 2 ? 0 : Math.sqrt(squaredDeviationSum / (count - 1) / count);
        }
    }

    private static final double FIXTURE_RIVER_BET_BB = 8;

    private static final class Discovery {
        int count;
        double incrementSum;

        void add(double increment) {
            count++;
            incrementSum += increment;
        }
    }

    private record HeldOut(
            String fineKey,
            String textureKey,
            String coarseKey,
            String equityKey,
            double betIncrementBb) {}

    private PhysicalRiverHeldOutDecisionAudit() {}

    public static Report assess(int sampledBoards, int minimumDiscoveryBoards, long seed) {
        return assess(
                sampledBoards,
                minimumDiscoveryBoards,
                seed,
                ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3);
    }

    public static Report assess(
            int sampledBoards,
            int minimumDiscoveryBoards,
            long seed,
            ButtonBigBlindRangeValidationFixture.RangeProfile profile) {
        return assess(sampledBoards, minimumDiscoveryBoards, seed, profile, 1.0);
    }

    public static Report assess(
            int sampledBoards,
            int minimumDiscoveryBoards,
            long seed,
            ButtonBigBlindRangeValidationFixture.RangeProfile profile,
            double buttonCallProbability) {
        return assess(
                sampledBoards,
                minimumDiscoveryBoards,
                seed,
                profile,
                buttonCallProbability,
                ResponseModel.HAND_INDEPENDENT_CALL);
    }

    public static Report assess(
            int sampledBoards,
            int minimumDiscoveryBoards,
            long seed,
            ButtonBigBlindRangeValidationFixture.RangeProfile profile,
            double buttonCallProbability,
            ResponseModel responseModel) {
        if (sampledBoards < 4 || sampledBoards > 1_000_000 || sampledBoards % 2 != 0)
            throw new IllegalArgumentException("Expected an even 4-1000000 sampled boards");
        if (minimumDiscoveryBoards < 1 || minimumDiscoveryBoards > sampledBoards / 2)
            throw new IllegalArgumentException("Invalid discovery-support threshold");
        if (!Double.isFinite(buttonCallProbability)
                || buttonCallProbability < 0
                || buttonCallProbability > 1)
            throw new IllegalArgumentException("Call probability must be in [0,1]");
        java.util.Objects.requireNonNull(responseModel, "responseModel");
        if (responseModel == ResponseModel.PAIR_OR_BETTER_CALL && buttonCallProbability != 1.0)
            throw new IllegalArgumentException("Pair-call response does not use call probability");
        var fine =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.BOARD_BUCKETS, profile);
        var texture =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.TEXTURE_BOARD_BUCKETS,
                        profile);
        var coarse =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var equity =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS,
                        profile);
        var deals = fine.chanceOutcomes(fine.initialState());
        double halfPotBb = fine.potBb() / 2;
        Map<String, Discovery> fineDiscovery = new HashMap<>();
        Map<String, Discovery> textureDiscovery = new HashMap<>();
        Map<String, Discovery> coarseDiscovery = new HashMap<>();
        Map<String, Discovery> equityDiscovery = new HashMap<>();
        List<HeldOut> heldOut = new ArrayList<>(sampledBoards / 2);
        SplittableRandom random = new SplittableRandom(seed);
        for (int attempt = 0; attempt < sampledBoards; attempt++) {
            var state = PhysicalRiverAliasAudit.sampleRiverState(fine, random);
            double betIncrement =
                    switch (responseModel) {
                        case HAND_INDEPENDENT_CALL ->
                                betIncrement(
                                        PhysicalRiverAliasAudit.calledBetMargin(state, deals),
                                        halfPotBb,
                                        FIXTURE_RIVER_BET_BB,
                                        buttonCallProbability);
                        case PAIR_OR_BETTER_CALL ->
                                PhysicalRiverPairCallResponse.betIncrement(
                                        state, deals, halfPotBb, FIXTURE_RIVER_BET_BB);
                    };
            String fineKey = fine.informationSet(state);
            String textureKey = texture.informationSet(state);
            String coarseKey = coarse.informationSet(state);
            String equityKey = equity.informationSet(state);
            if (attempt % 2 == 0) {
                fineDiscovery.computeIfAbsent(fineKey, key -> new Discovery()).add(betIncrement);
                textureDiscovery
                        .computeIfAbsent(textureKey, key -> new Discovery())
                        .add(betIncrement);
                coarseDiscovery
                        .computeIfAbsent(coarseKey, key -> new Discovery())
                        .add(betIncrement);
                equityDiscovery
                        .computeIfAbsent(equityKey, key -> new Discovery())
                        .add(betIncrement);
            } else
                heldOut.add(new HeldOut(fineKey, textureKey, coarseKey, equityKey, betIncrement));
        }
        return new Report(
                profile,
                seed,
                sampledBoards,
                minimumDiscoveryBoards,
                buttonCallProbability,
                responseModel,
                evaluate(heldOut, fineDiscovery, minimumDiscoveryBoards, HeldOut::fineKey),
                evaluate(heldOut, textureDiscovery, minimumDiscoveryBoards, HeldOut::textureKey),
                evaluate(heldOut, coarseDiscovery, minimumDiscoveryBoards, HeldOut::coarseKey),
                evaluate(heldOut, equityDiscovery, minimumDiscoveryBoards, HeldOut::equityKey),
                comparePaired(heldOut, coarseDiscovery, equityDiscovery, minimumDiscoveryBoards));
    }

    private static ModeResult evaluate(
            List<HeldOut> heldOut,
            Map<String, Discovery> discovery,
            int minimum,
            java.util.function.Function<HeldOut, String> key) {
        int supported = 0;
        double selectedIncrement = 0;
        double oracleIncrement = 0;
        SampleMoments regret = new SampleMoments();
        for (HeldOut board : heldOut) {
            Discovery bucket = discovery.get(key.apply(board));
            boolean hasSupport = bucket != null && bucket.count >= minimum;
            if (hasSupport) {
                supported++;
                if (bucket.incrementSum > 0) selectedIncrement += board.betIncrementBb();
            }
            double oracle = Math.max(0, board.betIncrementBb());
            oracleIncrement += oracle;
            regret.add(
                    oracle - (hasSupport && bucket.incrementSum > 0 ? board.betIncrementBb() : 0));
        }
        int count = heldOut.size();
        return new ModeResult(
                discovery.size(),
                supported,
                count,
                selectedIncrement / count,
                oracleIncrement / count,
                regret.standardError());
    }

    private static PairedGainComparison comparePaired(
            List<HeldOut> heldOut,
            Map<String, Discovery> coarseDiscovery,
            Map<String, Discovery> equityDiscovery,
            int minimum) {
        SampleMoments difference = new SampleMoments();
        for (HeldOut board : heldOut) {
            double increment = board.betIncrementBb();
            double equityGain =
                    selectsBet(equityDiscovery.get(board.equityKey()), minimum) ? increment : 0;
            double coarseGain =
                    selectsBet(coarseDiscovery.get(board.coarseKey()), minimum) ? increment : 0;
            difference.add(equityGain - coarseGain);
        }
        return new PairedGainComparison(difference.mean(), difference.standardError());
    }

    private static boolean selectsBet(Discovery bucket, int minimum) {
        return bucket != null && bucket.count >= minimum && bucket.incrementSum > 0;
    }

    /** Bet EV minus check EV when BTN's call probability does not depend on its private hand. */
    static double betIncrement(
            double showdownMargin,
            double halfPotBb,
            double riverBetBb,
            double buttonCallProbability) {
        return (1 - buttonCallProbability) * halfPotBb * (1 - showdownMargin)
                + buttonCallProbability * riverBetBb * showdownMargin;
    }
}
