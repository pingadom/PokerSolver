package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class PhysicalStrictConnectedStreetDeviationAuditTest {
    private static final ButtonBigBlindPhysicalDeckGame GAME =
            ButtonBigBlindRangeValidationFixture.createCoarseBucketed();

    @Test
    void accountsForEveryPhysicalDealAndReproducesHeldOutStreetGain() {
        var solution = solve(300);
        for (var street :
                new PhysicalConnectedStreetDeviationAudit.Street[] {
                    PhysicalConnectedStreetDeviationAudit.Street.FLOP,
                    PhysicalConnectedStreetDeviationAudit.Street.TURN,
                    PhysicalConnectedStreetDeviationAudit.Street.RIVER
                }) {
            var report =
                    PhysicalStrictConnectedStreetDeviationAudit.assess(
                            GAME, solution, street, 2_000, 2, 2, 43);
            assertEquals(
                    report,
                    PhysicalStrictConnectedStreetDeviationAudit.assess(
                            GAME, solution, street, 2_000, 2, 2, 43));
            assertEquals(
                    2_000,
                    report.terminalBeforeStreet()
                            + report.missingReachPolicy()
                            + report.reachedStreet());
            assertTrue(report.reachedStreet() > 0);
            assertTrue(report.evaluatedStates() > 0);
            assertTrue(report.evaluatedStates() <= report.reachedStreet());
            assertTrue(report.supportedHeldOutStates() <= report.heldOutStates());
            assertTrue(report.heldOutStates() <= report.evaluatedStates());
            assertTrue(report.evaluatedReachRate() <= 1);
            assertTrue(report.supportedHeldOutRate() <= 1);
        }
    }

    @Test
    void missingReachAndContinuationPoliciesNeverReceiveUniformFallback() {
        var full = solve(300);
        var empty = new CfrSolution(1, Map.of());
        var noReach =
                PhysicalStrictConnectedStreetDeviationAudit.assess(
                        GAME,
                        empty,
                        PhysicalConnectedStreetDeviationAudit.Street.FLOP,
                        500,
                        1,
                        1,
                        42);
        assertEquals(500, noReach.missingReachPolicy());
        assertEquals(0, noReach.reachedStreet());
        assertTrue(Double.isNaN(noReach.selectedGainBb()));

        var withoutButtonPostflop =
                new CfrSolution(
                        full.iterations(),
                        full.strategy().entrySet().stream()
                                .filter(entry -> !entry.getKey().startsWith("1:B:F:"))
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        var noContinuation =
                PhysicalStrictConnectedStreetDeviationAudit.assess(
                        GAME,
                        withoutButtonPostflop,
                        PhysicalConnectedStreetDeviationAudit.Street.FLOP,
                        1_000,
                        1,
                        1,
                        42);
        assertTrue(noContinuation.reachedStreet() > 0);
        assertEquals(0, noContinuation.evaluatedStates());
        assertTrue(noContinuation.missingCheckContinuation() > 0);
        assertTrue(noContinuation.missingBetContinuation() > 0);
    }

    @Test
    void rejectsInvalidSamplingSettings() {
        var solution = solve(1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalStrictConnectedStreetDeviationAudit.assess(
                                GAME,
                                solution,
                                PhysicalConnectedStreetDeviationAudit.Street.TURN,
                                1,
                                1,
                                1,
                                42));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalStrictConnectedStreetDeviationAudit.assess(
                                GAME,
                                solution,
                                PhysicalConnectedStreetDeviationAudit.Street.TURN,
                                100,
                                0,
                                1,
                                42));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalStrictConnectedStreetDeviationAudit.assess(
                                GAME,
                                solution,
                                PhysicalConnectedStreetDeviationAudit.Street.TURN,
                                100,
                                1,
                                51,
                                42));
    }

    @Test
    void riverContinuationEnumeratesBothPlayersActionsExactly() {
        var dealt = GAME.chanceOutcomes(GAME.initialState()).getFirst().state();
        var first =
                new ButtonBigBlindPhysicalDeckGame.State(
                        dealt.bigBlind(),
                        dealt.button(),
                        "oc",
                        List.of(Card.parse("2c"), Card.parse("7s"), Card.parse("Th")),
                        "kk",
                        Card.parse("3d"),
                        "kk",
                        Card.parse("9c"),
                        "");
        var afterCheck = GAME.afterAction(first, "k");
        var afterCheckBet = GAME.afterAction(afterCheck, "b");
        var afterBet = GAME.afterAction(first, "b");
        var solution =
                new CfrSolution(
                        1,
                        Map.of(
                                "1:" + GAME.informationSet(afterCheck),
                                Map.of("k", 0.75, "b", 0.25),
                                "0:" + GAME.informationSet(afterCheckBet),
                                Map.of("c", 0.4, "f", 0.6),
                                "1:" + GAME.informationSet(afterBet),
                                Map.of("c", 0.6, "f", 0.4)));
        double expectedCheck =
                0.75 * GAME.terminalUtility(GAME.afterAction(afterCheck, "k"))
                        + 0.25
                                * (0.4 * GAME.terminalUtility(GAME.afterAction(afterCheckBet, "c"))
                                        + 0.6
                                                * GAME.terminalUtility(
                                                        GAME.afterAction(afterCheckBet, "f")));
        double expectedBet =
                0.6 * GAME.terminalUtility(GAME.afterAction(afterBet, "c"))
                        + 0.4 * GAME.terminalUtility(GAME.afterAction(afterBet, "f"));
        assertEquals(
                expectedCheck,
                PhysicalStrictConnectedStreetDeviationAudit.exactRiver(GAME, solution, afterCheck)
                        .orElseThrow(),
                1e-12);
        assertEquals(
                expectedBet,
                PhysicalStrictConnectedStreetDeviationAudit.exactRiver(GAME, solution, afterBet)
                        .orElseThrow(),
                1e-12);
        var missing =
                new CfrSolution(
                        1,
                        Map.of(
                                "1:" + GAME.informationSet(afterCheck),
                                Map.of("k", 0.75, "b", 0.25)));
        assertTrue(
                PhysicalStrictConnectedStreetDeviationAudit.exactRiver(GAME, missing, afterCheck)
                        .isEmpty());
    }

    private static CfrSolution solve(int iterations) {
        return new CfrSolver<>(
                        GAME,
                        CfrSolver.Variant.VANILLA,
                        CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                        42)
                .solve(iterations);
    }
}
