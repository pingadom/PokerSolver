package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicalRiverAliasAuditTest {
    @Test
    void calledBetMarginMatchesExactTerminalPayoffForOneOpponent() {
        var game = ButtonBigBlindRangeValidationFixture.createBucketed();
        var deal = game.chanceOutcomes(game.initialState()).getFirst();
        var state = game.afterAction(game.afterAction(deal.state(), "open3"), "call");
        state = game.sampleChanceOutcome(state, 0.4).state();
        state = game.afterAction(game.afterAction(state, "k"), "k");
        state = game.sampleChanceOutcome(state, 0.3).state();
        state = game.afterAction(game.afterAction(state, "k"), "k");
        state = game.sampleChanceOutcome(state, 0.2).state();

        var check = game.afterAction(game.afterAction(state, "k"), "k");
        var calledBet = game.afterAction(game.afterAction(state, "b"), "c");
        double margin = PhysicalRiverAliasAudit.calledBetMargin(state, List.of(deal));
        assertEquals((game.terminalUtility(calledBet) - game.terminalUtility(check)) / 8, margin);
    }

    @Test
    void aliasReportIsRepeatableAndCoarseGroupingCannotRemoveConflictedBoards() {
        var report = PhysicalRiverAliasAudit.assess(2_000, 42);
        assertEquals(report, PhysicalRiverAliasAudit.assess(2_000, 42));
        assertTrue(report.comparedBoards() > 0);
        assertTrue(report.coarseBuckets() < report.fineBuckets());
        assertTrue(report.coarseConflictedBoards() >= report.fineConflictedBoards());
        assertTrue(report.coarseConflictedRate() >= report.fineConflictedRate());
        assertTrue(report.coarseObservationLossBb() >= report.fineObservationLossBb() - 1e-9);
        assertTrue(report.fineObservationLossBb() >= 0);
        assertThrows(IllegalArgumentException.class, () -> PhysicalRiverAliasAudit.assess(1, 42));
    }
}
