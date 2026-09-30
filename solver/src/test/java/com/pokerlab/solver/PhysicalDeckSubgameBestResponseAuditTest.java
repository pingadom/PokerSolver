package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PhysicalDeckSubgameBestResponseAuditTest {
    private static final ButtonBigBlindPhysicalDeckGame PHYSICAL =
            ButtonBigBlindRangeValidationFixture.createCoarseBucketed();

    @Test
    void chanceRestrictionPreservesRootDealsAndCombinesRepeatedPhysicalOutcomes() {
        var subgame =
                new PhysicalDeckChanceSubgame(
                        PHYSICAL, List.of(0.1, 0.1, 0.9), List.of(0.2), List.of(0.3));
        assertEquals(
                PHYSICAL.chanceOutcomes(PHYSICAL.initialState()),
                subgame.chanceOutcomes(subgame.initialState()));
        var deal = subgame.chanceOutcomes(subgame.initialState()).getFirst().state();
        var afterCall = PHYSICAL.afterAction(PHYSICAL.afterAction(deal, "open3"), "call");
        var flops = subgame.chanceOutcomes(afterCall);
        assertEquals(2, flops.size());
        assertEquals(2.0 / 3, flops.getFirst().probability(), 1e-12);
        assertEquals(1.0 / 3, flops.getLast().probability(), 1e-12);
        var afterFlopChecks =
                PHYSICAL.afterAction(PHYSICAL.afterAction(flops.getFirst().state(), "k"), "k");
        var turns = subgame.chanceOutcomes(afterFlopChecks);
        assertEquals(1, turns.size());
        assertEquals(
                PHYSICAL.sampleChanceOutcome(afterFlopChecks, 0.2).state(),
                turns.getFirst().state());
        var afterTurnChecks =
                PHYSICAL.afterAction(PHYSICAL.afterAction(turns.getFirst().state(), "k"), "k");
        assertEquals(
                PHYSICAL.sampleChanceOutcome(afterTurnChecks, 0.3).state(),
                subgame.chanceOutcomes(afterTurnChecks).getFirst().state());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PhysicalDeckChanceSubgame(
                                PHYSICAL, List.of(1.0), List.of(0.2), List.of(0.3)));
    }

    @Test
    void exactSubgameResponseReportsUniformFallbackAndRejectsTooSmallBudget() {
        var subgame =
                new PhysicalDeckChanceSubgame(PHYSICAL, List.of(0.1), List.of(0.2), List.of(0.3));
        var sparse = new CfrSolution(1, Map.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalDeckSubgameBestResponseAudit.assess(subgame, sparse, 10));
        var report = PhysicalDeckSubgameBestResponseAudit.assess(subgame, sparse, 100_000);
        assertEquals(PHYSICAL.contentHash(), report.physicalGameHash());
        assertEquals(0, report.learnedInformationSets());
        assertTrue(report.completedInformationSets() > 0);
        assertEquals(1, report.fallbackPathProbability(), 1e-12);
        assertTrue(report.bestResponse().gap() >= 0);
        assertTrue(
                report.bestResponse().firstBestResponse() >= report.bestResponse().profileValue());
        assertTrue(
                report.bestResponse().profileValue() >= report.bestResponse().secondBestResponse());
        assertEquals(report, PhysicalDeckSubgameBestResponseAudit.assess(subgame, sparse, 100_000));
        var deal = subgame.chanceOutcomes(subgame.initialState()).getFirst().state();
        var invalid =
                new CfrSolution(
                        1,
                        Map.of(
                                "1:" + subgame.informationSet(deal),
                                Map.of("open3", 0.8, "fold", 0.8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalDeckSubgameBestResponseAudit.assess(subgame, invalid, 100_000));
    }

    @Test
    void fallbackPathMassDistinguishesMissingKeysFromReachedMissingKeys() {
        var subgame =
                new PhysicalDeckChanceSubgame(PHYSICAL, List.of(0.1), List.of(0.2), List.of(0.3));
        var alwaysFoldButton =
                new CfrSolution(
                        1,
                        subgame.chanceOutcomes(subgame.initialState()).stream()
                                .map(outcome -> outcome.state())
                                .collect(
                                        Collectors.toMap(
                                                state -> "1:" + subgame.informationSet(state),
                                                state -> Map.of("open3", 0.0, "fold", 1.0),
                                                (first, second) -> first)));
        var report =
                PhysicalDeckSubgameBestResponseAudit.assess(subgame, alwaysFoldButton, 100_000);
        assertTrue(report.learnedInformationSets() > 0);
        assertTrue(report.completedInformationSets() > 0);
        assertEquals(0, report.fallbackPathProbability(), 1e-12);
        assertTrue(report.bestResponse().gap() > 0);
    }
}
