package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPreflopRangeWeightSensitivityTest {
    static SixMaxPreflopSolutionPack variant(
            SixMaxPreflopSolutionPack source, double weight, int iterations) {
        var ranges = new ArrayList<>(source.spot().ranges());
        var button = new ArrayList<>(ranges.get(3));
        var combo = button.getFirst();
        button.set(0, new WeightedCombo(combo.first(), combo.second(), weight));
        ranges.set(3, button);
        var spot =
                new SixMaxPreflopResearchSpot(
                        "weight-variant",
                        source.spot().rules(),
                        ranges,
                        source.spot().rake(),
                        source.spot().continuationModel());
        return SixMaxPreflopPayoffReuse.buildExact(
                        source,
                        spot,
                        iterations,
                        CfrSolver.Variant.CFR_PLUS,
                        SixMaxPreflopPayoffReuseTest.TIME)
                .pack();
    }

    private static SixMaxPreflopSolutionPack deterministicPolicy(
            SixMaxPreflopSolutionPack source, boolean rootCalls) {
        var game = source.rebuildGame();
        String rootKey =
                "0:"
                        + game.informationSet(
                                game.chanceOutcomes(game.initialState()).getFirst().state());
        var rows = new HashMap<String, Map<String, Double>>();
        for (var entry : source.solution().strategy().entrySet()) {
            String selected =
                    rootCalls && entry.getKey().equals(rootKey)
                            ? "call"
                            : entry.getValue().containsKey("fold") ? "fold" : "check";
            var row = new LinkedHashMap<String, Double>();
            for (var action : entry.getValue().keySet())
                row.put(action, action.equals(selected) ? 1.0 : 0.0);
            rows.put(entry.getKey(), row);
        }
        var policy = new CfrSolution(source.solution().iterations(), rows);
        return new SixMaxPreflopSolutionPack(
                source.schemaVersion(),
                source.solverVersion(),
                source.publicationStatus(),
                source.generatedAt(),
                source.spot(),
                source.spotHash(),
                source.payoffMethod(),
                source.payoffSeed(),
                policy,
                source.payoffs(),
                MultiPlayerInformationSetBestResponse.assess(game, policy).nashConvBb(),
                source.maxTerminalPayoffSEBb());
    }

    @Test
    void selfComparisonHasZeroPhysicalAndPolicyVariationAndNoWeightChanges() {
        var source = SixMaxPreflopPayoffReuseTest.source();
        var report = SixMaxPreflopRangeWeightSensitivity.assess(source, source);
        assertTrue(report.weightChanges().isEmpty());
        assertEquals(0, report.jointDealTotalVariation());
        assertEquals(0, report.policyDifference().maximumTotalVariation());
        assertEquals(0, report.policyDifference().reachWeightedTotalVariation());
        assertEquals(report.baselinePackHash(), report.variantPackHash());
        assertEquals(
                report.policyDifference().baselineExpectedDecisionEncounters(),
                report.policyDifference().variantExpectedDecisionEncounters());
    }

    @Test
    void separatesChangedChanceWeightsFromAnalyticRootActionDisagreement() {
        var baseline = SixMaxPreflopPayoffReuseTest.source();
        var changed = variant(baseline, 3, baseline.solution().iterations());
        var oldPolicy = deterministicPolicy(baseline, false);
        var newPolicy = deterministicPolicy(changed, true);
        var report = SixMaxPreflopRangeWeightSensitivity.assess(oldPolicy, newPolicy);
        assertEquals(2, report.jointDeals());
        assertEquals(.25, report.jointDealTotalVariation(), 1e-12);
        assertEquals(1, report.weightChanges().size());
        assertEquals(PreflopAllInSpot.Seat.BTN, report.weightChanges().getFirst().seat());
        assertEquals(1, report.weightChanges().getFirst().baselineWeight());
        assertEquals(3, report.weightChanges().getFirst().variantWeight());
        var difference = report.policyDifference();
        assertEquals(5, difference.baselineExpectedDecisionEncounters(), 1e-12);
        assertEquals(6, difference.variantExpectedDecisionEncounters(), 1e-12);
        assertEquals(1, difference.maximumTotalVariation());
        assertEquals(
                1.0 / difference.informationSets(), difference.uniformMeanTotalVariation(), 1e-12);
        assertEquals(1.0 / 5.5, difference.reachWeightedTotalVariation(), 1e-12);
        assertTrue(difference.maximumDifferenceInformationSet().startsWith("0:"));
        var reversed = SixMaxPreflopRangeWeightSensitivity.assess(newPolicy, oldPolicy);
        assertEquals(report.jointDealTotalVariation(), reversed.jointDealTotalVariation());
        assertEquals(
                difference.reachWeightedTotalVariation(),
                reversed.policyDifference().reachWeightedTotalVariation());
    }

    @Test
    void rejectsDifferentBettingGamesBudgetsPayoffsAndPhysicalSupport() {
        var baseline = SixMaxPreflopPayoffReuseTest.source();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopRangeWeightSensitivity.assess(
                                baseline, variant(baseline, 2, 7)));
        var otherRules =
                SixMaxPreflopPayoffReuseTest.spot(baseline.spot().ranges(), 9, CashRakeRule.none());
        var other =
                SixMaxPreflopPayoffReuse.buildExact(
                                baseline,
                                otherRules,
                                baseline.solution().iterations(),
                                CfrSolver.Variant.CFR_PLUS,
                                SixMaxPreflopPayoffReuseTest.TIME)
                        .pack();
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopRangeWeightSensitivity.assess(baseline, other));
        // Individually valid exact fixture, but a different showdown-share table.
        var differentPayoffs =
                SixMaxPreflopPackBuilder.build(
                        baseline.spot(),
                        baseline.solution().iterations(),
                        CfrSolver.Variant.CFR_PLUS,
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            shares[31 - Integer.numberOfLeadingZeros(mask)] = 1;
                            return new MultiwayShowdownEstimate(
                                    shares,
                                    new double[6],
                                    SixMaxPreflopSolutionPack.EXACT_BOARDS_PER_DEAL);
                        },
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        SixMaxPreflopPayoffReuseTest.TIME);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopRangeWeightSensitivity.assess(baseline, differentPayoffs));
        var ranges = new ArrayList<>(baseline.spot().ranges());
        ranges.set(3, java.util.List.of(ranges.get(3).getFirst()));
        var reducedSpot =
                new SixMaxPreflopResearchSpot(
                        "reduced",
                        baseline.spot().rules(),
                        ranges,
                        baseline.spot().rake(),
                        baseline.spot().continuationModel());
        var reduced =
                SixMaxPreflopPackBuilder.build(
                        reducedSpot,
                        baseline.solution().iterations(),
                        CfrSolver.Variant.CFR_PLUS,
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            shares[Integer.numberOfTrailingZeros(mask)] = 1;
                            return new MultiwayShowdownEstimate(
                                    shares,
                                    new double[6],
                                    SixMaxPreflopSolutionPack.EXACT_BOARDS_PER_DEAL);
                        },
                        MultiwaySolutionPack.EXACT_ENUMERATION,
                        0,
                        SixMaxPreflopPayoffReuseTest.TIME);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxPreflopRangeWeightSensitivity.assess(baseline, reduced));
    }
}
