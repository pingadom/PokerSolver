package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.Test;

class SixSeatRangeSourceTest {
    static final List<String> SINGLE = List.of("AcAd", "KcKd", "QcQd", "JcJd", "TcTd", "9c9d");
    static final List<String> CORRELATED =
            List.of("4c4d", "Kd9d,AhJh", "QhTh,AhKh", "JsTs,8s8h", "6c5c", "AcJc,7d7h");

    static SixSeatRangeSource.Request request(
            List<String> ranges, SixSeatPayoffWork.Configuration config) {
        return new SixSeatRangeSource.Request(
                "range-source-control",
                ranges,
                SixSeatRangePreflight.Limits.defaults(),
                new SixMaxPreflopBetting.Rules(2, .5, List.of(2.)),
                CashRakeRule.none(),
                new SixSeatRangeSource.PayoffRequest(
                        config.method(),
                        config.trialsPerDeal(),
                        Long.toString(config.seed()),
                        SixSeatPayoffWork.Budget.defaults()),
                2,
                CfrSolver.Variant.CFR_PLUS,
                SixSeatRangeSource.MAX_CFR_NODE_VISITS,
                "2026-11-18T20:00:00Z");
    }

    static SixSeatRangeSource.Request copy(
            SixSeatRangeSource.Request r,
            int iterations,
            CfrSolver.Variant variant,
            long nodes,
            String date) {
        return new SixSeatRangeSource.Request(
                r.id(),
                r.ranges(),
                r.preflightLimits(),
                r.rules(),
                r.rake(),
                r.payoff(),
                iterations,
                variant,
                nodes,
                date);
    }

    @Test
    void completeCfrWorkIncludesEveryWorldPublicStateAndSixAlternatingPasses() {
        var p =
                SixSeatRangeSource.plan(
                        request(CORRELATED, SixSeatPayoffWork.Configuration.shared(4, 711)));
        assertEquals(12, p.work().jointDeals());
        assertTrue(p.work().publicStates() > 0);
        assertEquals(1L + 12L * p.work().publicStates(), p.work().nodesPerPass());
        assertEquals(12 * p.work().nodesPerPass(), p.work().cfrNodeVisits());
        assertEquals(48, p.payoffPlan().work().boardEvaluations());
    }

    @Test
    void oneFewerCfrVisitRejectsBeforePhysicalExecutionAndEqualitySucceeds() {
        var r = request(SINGLE, SixSeatPayoffWork.Configuration.shared(4, 1));
        var p = SixSeatRangeSource.plan(r);
        var exact =
                SixSeatRangeSource.plan(
                        copy(
                                r,
                                r.iterations(),
                                r.variant(),
                                p.work().cfrNodeVisits(),
                                r.generatedAt()));
        assertEquals(p.contentHash(), exact.contentHash());
        var e =
                assertThrows(
                        SixSeatRangeSource.BudgetExceeded.class,
                        () ->
                                SixSeatRangeSource.plan(
                                        copy(
                                                r,
                                                r.iterations(),
                                                r.variant(),
                                                p.work().cfrNodeVisits() - 1,
                                                r.generatedAt())));
        assertEquals(p.work(), e.required());
        assertEquals(p.work().cfrNodeVisits() - 1, e.maximum());
    }

    @Test
    void freshCorrelatedSourceSolvesAuditsAndRemainsValidationOnly() {
        var p =
                SixSeatRangeSource.plan(
                        request(CORRELATED, SixSeatPayoffWork.Configuration.shared(4, 711)));
        var r = p.execute();
        assertFalse(r.reusedOwnedPayoffs());
        assertEquals(48L, r.payoffs().observedSharedBoards());
        assertEquals(684, r.pack().payoffs().size());
        assertEquals(MultiwaySolutionPack.VALIDATION_ONLY, r.pack().publicationStatus());
        assertEquals(p.work().cfrNodeVisits(), r.statistics().visitedNodes());
        assertEquals(0, r.statistics().sampledChanceNodes());
        assertEquals(6, r.quality().deviationGainsBb().size());
        assertTrue(Double.isFinite(r.quality().nashConvBb()));
        assertEquals(r.quality().nashConvBb(), r.pack().nashConvBb(), 1e-12);
        assertEquals(
                0.,
                r.quality().profileUtilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
        assertFalse(r.pack().solution().strategy().isEmpty());
        var restored = MultiwayPackJson.readFullRound(r.packJson());
        assertEquals(r.packHash(), MultiwayPackJson.fullRoundContentHash(restored));
    }

    @Test
    void ownedPayoffReuseMatchesTheExistingBuilderWithoutResamplingOrPolicyTransfer() {
        var p =
                SixSeatRangeSource.plan(
                        request(SINGLE, SixSeatPayoffWork.Configuration.shared(4, 7)));
        var payoffs = p.payoffPlan().execute();
        var r = p.solveWith(payoffs);
        assertTrue(r.reusedOwnedPayoffs());
        assertSame(payoffs, r.payoffs());
        var expected =
                SixMaxPreflopPackBuilder.build(
                        p.spot(),
                        p.request().iterations(),
                        p.request().variant(),
                        payoffs.oracle(),
                        SixMaxPreflopSolutionPack.SHARED_BOARD_MONTE_CARLO,
                        7,
                        p.request().generatedAt());
        assertEquals(MultiwayPackJson.writeFullRound(expected), r.packJson());
        assertEquals(4L, payoffs.observedSharedBoards());
    }

    @Test
    void mismatchedOwnedPayoffMethodSeedAndInputsCannotEnterTheSolve() {
        var p =
                SixSeatRangeSource.plan(
                        request(SINGLE, SixSeatPayoffWork.Configuration.shared(2, 7)));
        var other =
                SixSeatRangeSource.plan(
                                request(SINGLE, SixSeatPayoffWork.Configuration.shared(2, 8)))
                        .payoffPlan()
                        .execute();
        assertThrows(IllegalArgumentException.class, () -> p.solveWith(other));
        var ranges = new ArrayList<>(SINGLE);
        ranges.set(0, "AcAd@0.5");
        var weighted =
                SixSeatRangeSource.plan(
                                request(ranges, SixSeatPayoffWork.Configuration.shared(2, 7)))
                        .payoffPlan()
                        .execute();
        assertThrows(IllegalArgumentException.class, () -> p.solveWith(weighted));
        assertThrows(NullPointerException.class, () -> p.solveWith(null));
    }

    @Test
    void identityBindsRulesRakeIterationsAndVariantButNotTimestampOrSuccessfulBudget() {
        var r = request(SINGLE, SixSeatPayoffWork.Configuration.shared(2, 0));
        var p = SixSeatRangeSource.plan(r);
        assertEquals(
                p.contentHash(),
                SixSeatRangeSource.plan(
                                copy(
                                        r,
                                        2,
                                        r.variant(),
                                        p.work().cfrNodeVisits(),
                                        "2026-11-18T21:00:00Z"))
                        .contentHash());
        assertNotEquals(
                p.contentHash(),
                SixSeatRangeSource.plan(
                                copy(r, 3, r.variant(), r.maximumCfrNodeVisits(), r.generatedAt()))
                        .contentHash());
        assertNotEquals(
                p.contentHash(),
                SixSeatRangeSource.plan(
                                copy(
                                        r,
                                        2,
                                        CfrSolver.Variant.VANILLA,
                                        r.maximumCfrNodeVisits(),
                                        r.generatedAt()))
                        .contentHash());
        var changed =
                new SixSeatRangeSource.Request(
                        r.id(),
                        r.ranges(),
                        r.preflightLimits(),
                        r.rules(),
                        new CashRakeRule(.05, 1, true),
                        r.payoff(),
                        r.iterations(),
                        r.variant(),
                        r.maximumCfrNodeVisits(),
                        r.generatedAt());
        assertNotEquals(p.contentHash(), SixSeatRangeSource.plan(changed).contentHash());
    }

    @Test
    void sampledSourceRequiresTwoTrialsAndCanonicalFullWidthSeedStrings() {
        assertThrows(
                IllegalArgumentException.class,
                () -> request(SINGLE, SixSeatPayoffWork.Configuration.shared(1, 0)));
        for (String seed : List.of("01", "+1", "-0", "9223372036854775808"))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixSeatRangeSource.PayoffRequest(
                                    SixSeatPayoffWork.Method.SHARED_BOARD_MONTE_CARLO,
                                    2,
                                    seed,
                                    SixSeatPayoffWork.Budget.defaults()));
        var p = SixSeatRangeSource.plan(request(SINGLE, SixSeatPayoffWork.Configuration.exact()));
        assertEquals(658008, p.payoffPlan().work().boardEvaluations());
        assertEquals(0, p.request().payoff().trialsPerDeal());
    }

    @Test
    void invalidLimitsTimestampsAndUnsupportedHandsRejectBeforeAnySourceSolve() {
        var r = request(SINGLE, SixSeatPayoffWork.Configuration.shared(2, 0));
        for (int it : List.of(0, 10001))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> copy(r, it, r.variant(), r.maximumCfrNodeVisits(), r.generatedAt()));
        for (long n : List.of(0L, 200000001L))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> copy(r, 2, r.variant(), n, r.generatedAt()));
        assertThrows(
                IllegalArgumentException.class,
                () -> copy(r, 2, r.variant(), r.maximumCfrNodeVisits(), "not-a-date"));
        var ranges = new ArrayList<>(SINGLE);
        ranges.set(1, "KcKd,AcKh");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixSeatRangeSource.plan(
                                request(ranges, SixSeatPayoffWork.Configuration.shared(2, 0))));
    }

    @Test
    void freshExactSourceKeepsExactMethodZeroSeedAndZeroPayoffError() {
        var p = SixSeatRangeSource.plan(request(SINGLE, SixSeatPayoffWork.Configuration.exact()));
        var r = p.execute();
        assertEquals(MultiwaySolutionPack.EXACT_ENUMERATION, r.pack().payoffMethod());
        assertEquals(0, r.pack().payoffSeed());
        assertEquals(0., r.pack().maxTerminalPayoffSEBb());
        assertNull(r.payoffs().observedSharedBoards());
        assertEquals(658008, r.payoffs().boardsFromPerDealEstimates());
        assertEquals(p.work().cfrNodeVisits(), r.statistics().visitedNodes());
        assertEquals(57, r.pack().payoffs().size());
        r.pack().validate();
    }

    @Test
    void requestSourcePolicyAndQualityCollectionsAreDefensive() {
        var ranges = new ArrayList<>(SINGLE);
        var request = request(ranges, SixSeatPayoffWork.Configuration.shared(2, 0));
        ranges.clear();
        var r = SixSeatRangeSource.plan(request).execute();
        assertEquals(6, r.plan().request().ranges().size());
        assertThrows(
                UnsupportedOperationException.class, () -> r.plan().request().ranges().clear());
        assertThrows(
                UnsupportedOperationException.class, () -> r.pack().solution().strategy().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.pack().payoffs().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> r.quality().responseActions().getFirst().clear());
    }
}
