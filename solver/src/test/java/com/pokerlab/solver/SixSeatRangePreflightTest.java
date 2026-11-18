package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.*;
import org.junit.jupiter.api.Test;

class SixSeatRangePreflightTest {
    static final List<String> CORRELATED =
            List.of("4c4d", "Kd9d,AhJh", "QhTh,AhKh", "JsTs,8s8h", "6c5c", "AcJc,7d7h");
    static final List<String> SINGLE = List.of("AcAd", "KcKd", "QcQd", "JcJd", "TcTd", "9c9d");

    static SixSeatRangePreflight.Result plan(List<String> ranges) {
        return SixSeatRangePreflight.plan(ranges, SixSeatRangePreflight.Limits.defaults());
    }

    static String key(String cards) {
        return new WeightedCombo(
                        Card.parse(cards.substring(0, 2)), Card.parse(cards.substring(2)), 1)
                .key();
    }

    @Test
    void blockersProduceTheCompleteTwelveWorldPriorInsteadOfIndependentMarginals() {
        var p = plan(CORRELATED);
        assertEquals(16, p.nominalProducts());
        assertEquals(12, p.deals().size());
        assertEquals(31, p.work().candidateChecks());
        assertEquals(Math.log(12), p.logLegalWeightMass(), 1e-14);
        assertTrue(p.allInputCombosSupported());
        for (var d : p.deals()) {
            assertEquals(1. / 12, d.probability(), 1e-15);
            var seen = new HashSet<Card>();
            for (var c : d.hands()) {
                assertTrue(seen.add(c.first()));
                assertTrue(seen.add(c.second()));
            }
        }
        var hj = p.seatSupport().get(1).marginalProbabilities();
        var co = p.seatSupport().get(2).marginalProbabilities();
        assertEquals(2. / 3, hj.get(key("Kd9d")), 1e-15);
        assertEquals(1. / 3, co.get(key("AhKh")), 1e-15);
        assertTrue(hj.get(key("AhJh")) * co.get(key("AhKh")) > 0);
        assertTrue(
                p.deals().stream()
                        .noneMatch(
                                d ->
                                        d.hands().get(1).key().equals(key("AhJh"))
                                                && d.hands().get(2).key().equals(key("AhKh"))));
    }

    @Test
    void completeProbabilitiesAgreeWithALiteralCartesianProductOracle() {
        var ranges =
                List.of("4c4d", "Kd9d,AhJh@0.5", "QhTh,AhKh", "JsTs,8s8h", "6c5c", "AcJc,7d7h");
        var p = plan(ranges);
        var expected = new LinkedHashMap<List<String>, Double>();
        for (String hj : List.of("Kd9d", "AhJh"))
            for (String co : List.of("QhTh", "AhKh"))
                for (String btn : List.of("JsTs", "8s8h"))
                    for (String bb : List.of("AcJc", "7d7h")) {
                        var raw = List.of("4c4d", hj, co, btn, "6c5c", bb);
                        var seen = new HashSet<String>();
                        boolean legal = true;
                        for (String hand : raw)
                            for (String card : List.of(hand.substring(0, 2), hand.substring(2)))
                                if (!seen.add(card)) legal = false;
                        if (legal)
                            expected.put(
                                    raw.stream().map(SixSeatRangePreflightTest::key).toList(),
                                    hj.equals("AhJh") ? 0.5 : 1.);
                    }
        assertEquals(12, expected.size());
        double mass = expected.values().stream().mapToDouble(Double::doubleValue).sum();
        assertEquals(10, mass);
        for (var d : p.deals())
            assertEquals(
                    expected.remove(d.hands().stream().map(WeightedCombo::key).toList()) / mass,
                    d.probability(),
                    1e-15);
        assertTrue(expected.isEmpty());
    }

    @Test
    void exactWorkLimitsCompleteButOneFewerCheckReturnsNoPartialPlan() {
        var p = SixSeatRangePreflight.plan(CORRELATED, new SixSeatRangePreflight.Limits(12, 31));
        assertEquals(12, p.deals().size());
        var e =
                assertThrows(
                        SixSeatRangePreflight.Rejection.class,
                        () ->
                                SixSeatRangePreflight.plan(
                                        CORRELATED, new SixSeatRangePreflight.Limits(12, 30)));
        assertEquals(SixSeatRangePreflight.Reason.CANDIDATE_CHECK_CAP, e.reason());
        assertEquals(30, e.work().candidateChecks());
        assertEquals(11, e.work().completedLegalDeals());
        assertEquals(11, e.legalDealLowerBound());
    }

    @Test
    void worldCapKeepsTheWitnessOfAnAdditionalLegalWorldWithoutTruncation() {
        var e =
                assertThrows(
                        SixSeatRangePreflight.Rejection.class,
                        () ->
                                SixSeatRangePreflight.plan(
                                        CORRELATED, new SixSeatRangePreflight.Limits(11, 100)));
        assertEquals(SixSeatRangePreflight.Reason.JOINT_DEAL_CAP, e.reason());
        assertEquals(11, e.work().completedLegalDeals());
        assertEquals(12, e.legalDealLowerBound());
        var broad = List.of("22", "33", "44", "55", "66", "77");
        var cap = assertThrows(SixSeatRangePreflight.Rejection.class, () -> plan(broad));
        assertEquals(64, cap.work().completedLegalDeals());
        assertEquals(65, cap.legalDealLowerBound());
    }

    @Test
    void evenAnImpossibleProductIsBoundedAndNeverBecomesAUniformFallback() {
        var ranges = Collections.nCopies(6, "AA");
        var e = assertThrows(SixSeatRangePreflight.Rejection.class, () -> plan(ranges));
        assertEquals(SixSeatRangePreflight.Reason.NO_LEGAL_DEALS, e.reason());
        assertEquals(78, e.work().candidateChecks());
        assertEquals(0, e.legalDealLowerBound());
        var limited =
                assertThrows(
                        SixSeatRangePreflight.Rejection.class,
                        () ->
                                SixSeatRangePreflight.plan(
                                        ranges, new SixSeatRangePreflight.Limits(64, 20)));
        assertEquals(SixSeatRangePreflight.Reason.CANDIDATE_CHECK_CAP, limited.reason());
        assertEquals(20, limited.work().candidateChecks());
    }

    @Test
    void tinyCommonScalesUseLogWeightsWithoutLosingSupportedWorlds() {
        String tiny = "0." + "0".repeat(299) + "1";
        var input = SINGLE.stream().map(s -> s + "@" + tiny).toList();
        var p = plan(input);
        assertEquals(1, p.deals().size());
        assertEquals(1., p.deals().getFirst().probability());
        assertTrue(Double.isFinite(p.logLegalWeightMass()));
        assertTrue(p.logLegalWeightMass() < -4000);
    }

    @Test
    void unrepresentableRelativeProbabilitiesRejectRatherThanDropPositiveWorlds() {
        String tiny = "0." + "0".repeat(319) + "1";
        var input = new ArrayList<>(SINGLE);
        input.set(0, "AcAd,2c2d@" + tiny);
        input.set(1, "KcKd,3c3d@" + tiny);
        var e = assertThrows(SixSeatRangePreflight.Rejection.class, () -> plan(input));
        assertEquals(SixSeatRangePreflight.Reason.PROBABILITY_UNDERFLOW, e.reason());
        assertEquals(4, e.work().completedLegalDeals());
    }

    @Test
    void unsupportedInputCombosStayVisibleAndCannotBeSilentlyPrunedIntoASpot() {
        var input = new ArrayList<>(SINGLE);
        input.set(1, "KcKd,AcKh");
        var p = plan(input);
        assertFalse(p.allInputCombosSupported());
        assertEquals(List.of(key("AcKh")), p.seatSupport().get(1).unsupportedCombos());
        assertEquals(0., p.seatSupport().get(1).marginalProbabilities().get(key("AcKh")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        p.toResearchSpot(
                                "unsupported",
                                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                                CashRakeRule.none()));
    }

    @Test
    void completePlansBridgeToTheExistingSpotWhileKeepingItsValidation() {
        var p = plan(SINGLE);
        var rules = new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));
        var spot = p.toResearchSpot("planned-input", rules, CashRakeRule.none());
        assertEquals(p.ranges(), spot.ranges());
        assertEquals(rules, spot.rules());
        assertEquals(SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN, spot.continuationModel());
        assertThrows(
                IllegalArgumentException.class,
                () -> p.toResearchSpot("INVALID ID", rules, CashRakeRule.none()));
    }

    @Test
    void identityIgnoresNotationAndSuccessfulBudgetsButPreservesSeatOrder() {
        var input = new ArrayList<>(CORRELATED);
        input.set(1, "AhJh@1,Kd9d");
        var a = plan(CORRELATED);
        var b = SixSeatRangePreflight.plan(input, new SixSeatRangePreflight.Limits(12, 31));
        assertEquals(a.inputHash(), b.inputHash());
        assertEquals(a.deals(), b.deals());
        Collections.swap(input, 1, 2);
        assertNotEquals(a.inputHash(), plan(input).inputHash());
    }

    @Test
    void weightedCorrelatedPlanMatchesEveryExistingGameChanceOutcome() {
        var input = new ArrayList<>(CORRELATED);
        input.set(1, "Kd9d,AhJh@0.5");
        var p = plan(input);
        var rules = new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));
        var spot = p.toResearchSpot("correlated-input", rules, CashRakeRule.none());
        int[] calls = {0};
        var game =
                spot.game(
                        (hands, mask) -> {
                            calls[0]++;
                            double[] shares = new double[6];
                            for (int seat = 0; seat < 6; seat++)
                                if ((mask & (1 << seat)) != 0)
                                    shares[seat] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        assertEquals(12 * 57, calls[0]);
        var expected = new HashMap<List<String>, Double>();
        for (var d : p.deals())
            expected.put(d.hands().stream().map(WeightedCombo::key).toList(), d.probability());
        var outcomes = game.chanceOutcomes(game.initialState());
        assertEquals(12, outcomes.size());
        for (var outcome : outcomes) {
            var key = game.dealtHands(outcome.state()).stream().map(WeightedCombo::key).toList();
            assertEquals(expected.remove(key), outcome.probability(), 1e-15);
        }
        assertTrue(expected.isEmpty());
        assertEquals(
                SixMaxPreflopCheckdownGame.ChanceModel.EXACT_RANGE_PRODUCT, game.chanceModel());
        assertEquals(0, game.chanceSamples());
    }

    @Test
    void argumentsAndLimitsCannotIncreaseOriginalCaps() {
        for (var limit :
                List.of(
                        new int[] {0, 1},
                        new int[] {65, 100},
                        new int[] {64, 0},
                        new int[] {64, 1000001}))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixSeatRangePreflight.Limits(limit[0], limit[1]));
        assertThrows(IllegalArgumentException.class, () -> plan(null));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of("AA")));
        assertThrows(
                IllegalArgumentException.class, () -> SixSeatRangePreflight.plan(SINGLE, null));
        var invalid = new ArrayList<>(SINGLE);
        invalid.set(0, "AK");
        assertThrows(IllegalArgumentException.class, () -> plan(invalid));
    }

    @Test
    void planDealAndMarginalCollectionsAreImmutableAndDetached() {
        var input = new ArrayList<>(CORRELATED);
        var p = plan(input);
        input.clear();
        assertEquals(12, p.deals().size());
        assertThrows(UnsupportedOperationException.class, () -> p.ranges().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.ranges().getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.rangeHashes().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.deals().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> p.deals().getFirst().hands().clear());
        assertThrows(UnsupportedOperationException.class, () -> p.seatSupport().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> p.seatSupport().getFirst().marginalProbabilities().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> p.seatSupport().getFirst().unsupportedCombos().clear());
    }
}
