package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Sample-split decision check for a fixed called-bet river counterfactual. Decisions are chosen
 * using discovery boards only, then scored on independently sampled held-out boards. A bucket below
 * the support threshold chooses check. This does not evaluate equilibrium play.
 */
public final class PhysicalRiverHeldOutDecisionAudit {
    public record ModeResult(
            int discoveredBuckets,
            int heldOutSupportedBoards,
            int heldOutBoards,
            double selectedGainBb,
            double physicalOracleGainBb) {
        public double supportRate() {
            return heldOutBoards == 0 ? 0 : (double) heldOutSupportedBoards / heldOutBoards;
        }

        public double regretBb() {
            return physicalOracleGainBb - selectedGainBb;
        }
    }

    public record Report(
            ButtonBigBlindRangeValidationFixture.RangeProfile rangeProfile,
            long seed,
            int sampledBoards,
            int minimumDiscoveryBoards,
            ModeResult fine,
            ModeResult texture,
            ModeResult coarse) {}

    private static final double FIXTURE_RIVER_BET_BB = 8;

    private static final class Discovery {
        int count;
        double marginSum;

        void add(double margin) {
            count++;
            marginSum += margin;
        }
    }

    private record HeldOut(String fineKey, String textureKey, String coarseKey, double margin) {}

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
        if (sampledBoards < 4 || sampledBoards > 1_000_000 || sampledBoards % 2 != 0)
            throw new IllegalArgumentException("Expected an even 4-1000000 sampled boards");
        if (minimumDiscoveryBoards < 1 || minimumDiscoveryBoards > sampledBoards / 2)
            throw new IllegalArgumentException("Invalid discovery-support threshold");
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
        var deals = fine.chanceOutcomes(fine.initialState());
        Map<String, Discovery> fineDiscovery = new HashMap<>();
        Map<String, Discovery> textureDiscovery = new HashMap<>();
        Map<String, Discovery> coarseDiscovery = new HashMap<>();
        List<HeldOut> heldOut = new ArrayList<>(sampledBoards / 2);
        SplittableRandom random = new SplittableRandom(seed);
        for (int attempt = 0; attempt < sampledBoards; attempt++) {
            var state = PhysicalRiverAliasAudit.sampleRiverState(fine, random);
            double margin = PhysicalRiverAliasAudit.calledBetMargin(state, deals);
            String fineKey = fine.informationSet(state);
            String textureKey = texture.informationSet(state);
            String coarseKey = coarse.informationSet(state);
            if (attempt % 2 == 0) {
                fineDiscovery.computeIfAbsent(fineKey, key -> new Discovery()).add(margin);
                textureDiscovery.computeIfAbsent(textureKey, key -> new Discovery()).add(margin);
                coarseDiscovery.computeIfAbsent(coarseKey, key -> new Discovery()).add(margin);
            } else heldOut.add(new HeldOut(fineKey, textureKey, coarseKey, margin));
        }
        return new Report(
                profile,
                seed,
                sampledBoards,
                minimumDiscoveryBoards,
                evaluate(heldOut, fineDiscovery, minimumDiscoveryBoards, HeldOut::fineKey),
                evaluate(heldOut, textureDiscovery, minimumDiscoveryBoards, HeldOut::textureKey),
                evaluate(heldOut, coarseDiscovery, minimumDiscoveryBoards, HeldOut::coarseKey));
    }

    private static ModeResult evaluate(
            List<HeldOut> heldOut,
            Map<String, Discovery> discovery,
            int minimum,
            java.util.function.Function<HeldOut, String> key) {
        int supported = 0;
        double selectedMargin = 0;
        double oracleMargin = 0;
        for (HeldOut board : heldOut) {
            Discovery bucket = discovery.get(key.apply(board));
            boolean hasSupport = bucket != null && bucket.count >= minimum;
            if (hasSupport) {
                supported++;
                if (bucket.marginSum > 0) selectedMargin += board.margin();
            }
            oracleMargin += Math.max(0, board.margin());
        }
        int count = heldOut.size();
        return new ModeResult(
                discovery.size(),
                supported,
                count,
                FIXTURE_RIVER_BET_BB * selectedMargin / count,
                FIXTURE_RIVER_BET_BB * oracleMargin / count);
    }
}
