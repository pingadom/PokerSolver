package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SixSeatPayoffWorkTest {
    static final List<String> SINGLE = List.of("AcAd", "KcKd", "QcQd", "JcJd", "TcTd", "9c9d");
    static final List<String> CORRELATED =
            List.of("4c4d", "Kd9d,AhJh", "QhTh,AhKh", "JsTs,8s8h", "6c5c", "AcJc,7d7h");

    static SixSeatRangePreflight.Result input(List<String> ranges) {
        return SixSeatRangePreflight.plan(ranges, SixSeatRangePreflight.Limits.defaults());
    }

    static SixSeatPayoffWork.Plan plan(
            List<String> ranges, SixSeatPayoffWork.Configuration config) {
        return SixSeatPayoffWork.plan(input(ranges), config, SixSeatPayoffWork.Budget.defaults());
    }

    @Test
    void exactCountsShareOneBoardStreamAcrossAllFiftySevenSubsets() {
        long choose = 1;
        for (int i = 1; i <= 5; i++) choose = choose * (40 - i + 1) / i;
        assertEquals(658008, choose);
        assertEquals(
                57,
                java.util.stream.IntStream.range(1, 64)
                        .filter(m -> Integer.bitCount(m) >= 2)
                        .count());
        var p = plan(CORRELATED, SixSeatPayoffWork.Configuration.exact());
        assertEquals(12, p.work().jointDeals());
        assertEquals(choose, p.work().boardsPerDeal());
        assertEquals(7896096, p.work().boardEvaluations());
        assertEquals(47376576, p.work().handEvaluations());
        assertEquals(684, p.work().activeSubsetEntries());
    }

    @Test
    void exactBudgetEdgesRejectBeforeExecutionWithoutChangingMethod() {
        var i = input(CORRELATED);
        var c = SixSeatPayoffWork.Configuration.exact();
        var p = SixSeatPayoffWork.plan(i, c, new SixSeatPayoffWork.Budget(7896096, 47376576));
        assertEquals(SixSeatPayoffWork.Method.EXACT_ENUMERATION, p.configuration().method());
        for (var budget :
                List.of(
                        new SixSeatPayoffWork.Budget(7896095, 47376576),
                        new SixSeatPayoffWork.Budget(7896096, 47376575))) {
            var e =
                    assertThrows(
                            SixSeatPayoffWork.BudgetExceeded.class,
                            () -> SixSeatPayoffWork.plan(i, c, budget));
            assertEquals(p.work(), e.required());
            assertEquals(budget, e.budget());
        }
    }

    @Test
    void sharedExecutionMatchesPlannedPhysicalWorkAndCompleteSubsetRows() {
        var p = plan(CORRELATED, SixSeatPayoffWork.Configuration.shared(8, 711));
        assertEquals(96, p.work().boardEvaluations());
        assertEquals(576, p.work().handEvaluations());
        var r = p.execute();
        assertSame(p, r.plan());
        assertEquals(96, r.boardsFromPerDealEstimates());
        assertEquals(96L, r.observedSharedBoards());
        assertEquals(684, r.rows().size());
        for (var row : r.rows()) {
            assertEquals(8, row.estimate().trials());
            assertEquals(6, row.hands().size());
            assertEquals(1., Arrays.stream(row.estimate().shares()).sum(), 1e-12);
            for (int seat = 0; seat < 6; seat++) {
                assertTrue(Double.isFinite(row.estimate().standardErrors()[seat]));
                if ((row.activeMask() & (1 << seat)) == 0) {
                    assertEquals(0., row.estimate().shares()[seat]);
                    assertEquals(0., row.estimate().standardErrors()[seat]);
                }
            }
        }
        var spot =
                p.input()
                        .toResearchSpot(
                                "owned-sampled-input",
                                new SixMaxPreflopBetting.Rules(100, .5, List.of(100.)),
                                CashRakeRule.none());
        var game = spot.game(r.oracle());
        assertEquals(12, game.chanceOutcomes(game.initialState()).size());
        assertTrue(Double.isFinite(game.maximumTerminalPayoffStandardErrorBb()));
        assertEquals(96L, r.observedSharedBoards());
    }

    @Test
    void exactExecutionMatchesIndependentDeadCardHeadsUpEnumeration() {
        var p = plan(SINGLE, SixSeatPayoffWork.Configuration.exact());
        var r = p.execute();
        assertEquals(57, r.rows().size());
        assertEquals(658008, r.boardsFromPerDealEstimates());
        assertNull(r.observedSharedBoards());
        var hands = p.input().deals().getFirst().hands();
        var expected = ExactDeadCardHeadsUpShowdown.enumerate(hands, 3);
        assertArrayEquals(
                expected.estimate().shares(), r.oracle().estimate(hands, 3).shares(), 1e-12);
        for (var row : r.rows()) {
            assertEquals(658008, row.estimate().trials());
            assertArrayEquals(new double[6], row.estimate().standardErrors());
            assertEquals(1., Arrays.stream(row.estimate().shares()).sum(), 1e-10);
        }
    }

    @Test
    void successfulBudgetsAndEquivalentNotationPreserveIdentityButMethodAndSeedDoNot() {
        var input = input(CORRELATED);
        var c = SixSeatPayoffWork.Configuration.shared(8, Long.MIN_VALUE);
        var a = SixSeatPayoffWork.plan(input, c, SixSeatPayoffWork.Budget.defaults());
        var b = SixSeatPayoffWork.plan(input, c, new SixSeatPayoffWork.Budget(96, 576));
        assertEquals(a.contentHash(), b.contentHash());
        var expressions = new ArrayList<>(CORRELATED);
        expressions.set(1, "AhJh@1,Kd9d");
        assertEquals(a.contentHash(), plan(expressions, c).contentHash());
        assertNotEquals(
                a.contentHash(),
                plan(CORRELATED, SixSeatPayoffWork.Configuration.shared(8, Long.MAX_VALUE))
                        .contentHash());
        assertNotEquals(
                a.contentHash(),
                plan(CORRELATED, SixSeatPayoffWork.Configuration.shared(9, Long.MIN_VALUE))
                        .contentHash());
        assertNotEquals(
                a.contentHash(),
                plan(CORRELATED, SixSeatPayoffWork.Configuration.exact()).contentHash());
        assertEquals(Long.toString(Long.MIN_VALUE), c.metadata().get("seed"));
    }

    @Test
    void repeatedOwnedExecutionsAreDeterministic() {
        var p = plan(SINGLE, SixSeatPayoffWork.Configuration.shared(4, 123));
        var a = p.execute();
        var b = p.execute();
        assertEquals(4L, a.observedSharedBoards());
        assertEquals(4L, b.observedSharedBoards());
        for (int i = 0; i < a.rows().size(); i++) {
            var x = a.rows().get(i);
            var y = b.rows().get(i);
            assertEquals(x.hands(), y.hands());
            assertEquals(x.activeMask(), y.activeMask());
            assertArrayEquals(x.estimate().shares(), y.estimate().shares());
            assertArrayEquals(x.estimate().standardErrors(), y.estimate().standardErrors());
        }
    }

    @Test
    void ownedOracleRefusesUnknownDealsAndSubsetsAndCannotBeMutated() {
        var p = plan(SINGLE, SixSeatPayoffWork.Configuration.shared(4, 4));
        var r = p.execute();
        var hands = p.input().deals().getFirst().hands();
        var oracle = r.oracle();
        var value = oracle.estimate(hands, 3);
        double before = value.shares()[0];
        value.shares()[0] = -1;
        value.standardErrors()[0] = -1;
        assertEquals(before, oracle.estimate(hands, 3).shares()[0]);
        var swapped = new ArrayList<>(hands);
        Collections.swap(swapped, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> oracle.estimate(swapped, 3));
        for (int mask : List.of(0, 1, 64, 65, -1))
            assertThrows(IllegalArgumentException.class, () -> oracle.estimate(hands, mask));
        assertThrows(IllegalArgumentException.class, () -> oracle.estimate(null, 3));
        assertThrows(UnsupportedOperationException.class, () -> r.rows().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> r.rows().getFirst().hands().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> p.configuration().metadata().clear());
    }

    @Test
    void ownedPayoffLookupIgnoresFrequencyButPlanIdentityStillBindsIt() {
        var a = plan(SINGLE, SixSeatPayoffWork.Configuration.shared(2, 7));
        var result = a.execute();
        var hands = a.input().deals().getFirst().hands();
        var weighted =
                hands.stream().map(c -> new WeightedCombo(c.first(), c.second(), .5)).toList();
        assertSame(result.oracle().estimate(hands, 3), result.oracle().estimate(weighted, 3));
        var input = new ArrayList<>(SINGLE);
        input.set(0, "AcAd@0.5");
        assertNotEquals(a.contentHash(), plan(input, a.configuration()).contentHash());
    }

    @Test
    void unsupportedDeclaredHandsRemainVisibleRatherThanBecomingAdmittedInputs() {
        var ranges = new ArrayList<>(SINGLE);
        ranges.set(1, "KcKd,AcKh");
        var p = plan(ranges, SixSeatPayoffWork.Configuration.shared(2, 1));
        assertEquals(1, p.work().jointDeals());
        assertEquals(2, p.input().ranges().get(1).size());
        assertFalse(p.input().allInputCombosSupported());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        p.input()
                                .toResearchSpot(
                                        "unsupported",
                                        new SixMaxPreflopBetting.Rules(100, .5, List.of(100.)),
                                        CashRakeRule.none()));
    }

    @Test
    void hardBudgetsAndMethodArgumentsCannotBeExpandedOrMadeAmbiguous() {
        for (long[] v :
                List.of(
                        new long[] {0, 1},
                        new long[] {10000001, 1},
                        new long[] {1, 0},
                        new long[] {1, 60000001}))
            assertThrows(
                    IllegalArgumentException.class, () -> new SixSeatPayoffWork.Budget(v[0], v[1]));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixSeatPayoffWork.Configuration(
                                SixSeatPayoffWork.Method.EXACT_ENUMERATION, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixSeatPayoffWork.Configuration(
                                SixSeatPayoffWork.Method.EXACT_ENUMERATION, 0, 1));
        assertThrows(
                IllegalArgumentException.class, () -> SixSeatPayoffWork.Configuration.shared(0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixSeatPayoffWork.Configuration.shared(-1, 0));
        assertThrows(
                NullPointerException.class,
                () ->
                        SixSeatPayoffWork.plan(
                                null,
                                SixSeatPayoffWork.Configuration.exact(),
                                SixSeatPayoffWork.Budget.defaults()));
    }

    @Test
    void hugeSampleRequestsRejectWithLongWorkCountsAndNoNarrowing() {
        var e =
                assertThrows(
                        SixSeatPayoffWork.BudgetExceeded.class,
                        () ->
                                plan(
                                        CORRELATED,
                                        SixSeatPayoffWork.Configuration.shared(
                                                Integer.MAX_VALUE, 0)));
        assertEquals(12L * Integer.MAX_VALUE, e.required().boardEvaluations());
        assertEquals(72L * Integer.MAX_VALUE, e.required().handEvaluations());
    }
}
