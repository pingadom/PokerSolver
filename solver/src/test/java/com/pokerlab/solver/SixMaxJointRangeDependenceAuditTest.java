package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Deck;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxJointRangeDependenceAuditTest {
    @Test
    void largeMarginalProductIsMeasuredUsingOnlySupportedWorlds() {
        var worlds = new ArrayList<SixMaxJointDealSampler.JointDeal>();
        var deck = new Deck().cards();
        for (int offset = 0; offset < 52; offset++) {
            var hands = new ArrayList<WeightedCombo>();
            for (int seat = 0; seat < 6; seat++)
                hands.add(
                        new WeightedCombo(
                                deck.get((offset + 2 * seat) % 52),
                                deck.get((offset + 2 * seat + 1) % 52),
                                1));
            worlds.add(new SixMaxJointDealSampler.JointDeal(hands, 1));
        }
        var game =
                SixMaxPreflopCheckdownGame.fromSampledDeals(
                        new SixMaxPreflopBetting.Rules(2, .5, List.of(2.0)),
                        new SixMaxJointDealSampler.Sample(711, 52, 0, worlds),
                        CashRakeRule.none(),
                        (hands, mask) -> equalShares(mask));
        var report = SixMaxJointRangeDependenceAudit.assess(game);
        assertEquals(52, report.supportedJointDeals());
        assertEquals(19_770_609_664L, report.marginalProductDeals());
        assertEquals(5 * Math.log(52) / Math.log(2), report.totalCorrelationBits(), 1e-12);
        assertEquals(1 - Math.pow(52, -5), report.unsupportedIndependentMass(), 1e-14);
        assertEquals(
                report.unsupportedIndependentMass(), report.totalVariationFromIndependent(), 1e-14);
    }

    @Test
    void fullAuditRecoversTheKnownBlockerDependence() throws Exception {
        var game = SixMaxPrivateRangeCorrelationAuditTest.correlatedBase();
        var report = SixMaxJointRangeDependenceAudit.assess(game);
        assertEquals("EXACT_RANGE_PRODUCT", report.chanceModel());
        assertEquals(12, report.supportedJointDeals());
        assertEquals(16, report.marginalProductDeals());
        assertEquals(Math.log(12) / Math.log(2), report.jointEntropyBits(), 1e-14);
        assertEquals(Math.log(27.0 / 16) / (3 * Math.log(2)), report.totalCorrelationBits(), 1e-14);
        assertEquals(1.0 / 9, report.unsupportedIndependentMass(), 1e-14);
        assertEquals(2.0 / 9, report.totalVariationFromIndependent(), 1e-14);
        assertEquals(0, report.marginalEntropyBits().get(Seat.UTG));
        assertEquals(1, report.marginalEntropyBits().get(Seat.BTN), 1e-14);
        assertEquals(
                report.totalCorrelationBits(),
                report.marginalEntropyBits().values().stream()
                                .mapToDouble(Double::doubleValue)
                                .sum()
                        - report.jointEntropyBits(),
                1e-14);
        assertThrows(
                UnsupportedOperationException.class, () -> report.marginalEntropyBits().clear());
    }

    @Test
    void threeSeatParityIsDependentDespiteEveryPairBeingIndependent() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var original = base.dealtHands(base.chanceOutcomes(base.initialState()).getFirst().state());
        var worlds = new ArrayList<SixMaxJointDealSampler.JointDeal>();
        for (int a = 0; a < 2; a++)
            for (int b = 0; b < 2; b++) {
                var hands = new ArrayList<>(original);
                hands.set(0, SixMaxConnectedPreflopGameTest.combo(a == 0 ? "As Ah" : "Ac Ad", 1));
                hands.set(1, SixMaxConnectedPreflopGameTest.combo(b == 0 ? "Ks Kh" : "Kc Kd", 1));
                hands.set(
                        2,
                        SixMaxConnectedPreflopGameTest.combo((a ^ b) == 0 ? "Qs Qh" : "Qc Qd", 1));
                worlds.add(new SixMaxJointDealSampler.JointDeal(hands, 1));
            }
        var game =
                SixMaxPreflopCheckdownGame.fromSampledDeals(
                        base.rules(),
                        new SixMaxJointDealSampler.Sample(711, 4, 0, worlds),
                        CashRakeRule.none(),
                        (hands, mask) -> equalShares(mask));
        for (var pair : SixMaxPrivateRangeCorrelationAudit.assess(game).pairs()) {
            assertEquals(0, pair.mutualInformationBits(), 1e-14);
            assertEquals(0, pair.totalVariationFromIndependent(), 1e-14);
        }
        var report = SixMaxJointRangeDependenceAudit.assess(game);
        assertEquals("EMPIRICAL_JOINT_DEALS", report.chanceModel());
        assertTrue(
                report.interpretation()
                        .contains("empirical absence does not prove physical impossibility"));
        assertEquals(4, report.supportedJointDeals());
        assertEquals(8, report.marginalProductDeals());
        assertEquals(2, report.jointEntropyBits(), 1e-14);
        assertEquals(1, report.totalCorrelationBits(), 1e-14);
        assertEquals(.5, report.unsupportedIndependentMass(), 1e-14);
        assertEquals(.5, report.totalVariationFromIndependent(), 1e-14);
    }

    @Test
    void unequalIndependentWeightsHaveNoJointDependence() {
        var report = SixMaxJointRangeDependenceAudit.assess(SixMaxConnectedPreflopGameTest.base());
        assertEquals(2, report.supportedJointDeals());
        assertEquals(2, report.marginalProductDeals());
        assertEquals(0, report.totalCorrelationBits(), 1e-14);
        assertEquals(0, report.unsupportedIndependentMass(), 1e-14);
        assertEquals(0, report.totalVariationFromIndependent(), 1e-14);
        assertEquals(report.jointEntropyBits(), report.marginalEntropyBits().get(Seat.UTG), 1e-14);
    }

    @Test
    void totalCorrelationSurvivesIndependentProductUnderflow() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var hands = base.dealtHands(base.chanceOutcomes(base.initialState()).getFirst().state());
        var ranges = new ArrayList<>(hands.stream().map(List::of).toList());
        ranges.set(
                0,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("As Ah", 1),
                        SixMaxConnectedPreflopGameTest.combo("Kc Kd", 1e-150)));
        ranges.set(
                1,
                List.of(
                        SixMaxConnectedPreflopGameTest.combo("Kc 2h", 1),
                        SixMaxConnectedPreflopGameTest.combo("As 2d", 1e-150)));
        var game =
                new SixMaxPreflopCheckdownGame(
                        base.rules(),
                        ranges,
                        CashRakeRule.none(),
                        (dealt, mask) -> equalShares(mask));
        double rare =
                game.chanceOutcomes(game.initialState()).stream()
                        .mapToDouble(ChanceOutcome::probability)
                        .min()
                        .orElseThrow();
        assertTrue(rare > 0);
        assertEquals(0, rare * rare);
        var report = SixMaxJointRangeDependenceAudit.assess(game);
        assertTrue(Double.isFinite(report.totalCorrelationBits()));
        assertEquals(
                -rare * Math.log(rare) / Math.log(2), report.totalCorrelationBits(), rare * 1e-10);
    }

    private static MultiwayShowdownEstimate equalShares(int mask) {
        double[] shares = new double[6];
        for (int i = 0; i < 6; i++)
            if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
        return MultiwayShowdownEstimate.certain(shares);
    }
}
