package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxAlternatingContinuationSolverTest {
    @Test
    void rejectsAWholeRoundWhenChangedRangesLoseTheConditionalCertificate() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var lifted =
                SixMaxConnectedPreflopAudit.liftCheckdown(
                        game, SixMaxConditionalPostflopRefinementTest.uniform(game.source()));
        var initial =
                SixMaxConditionalPostflopRefinement.refine(game, lifted, 30, b -> {}).candidate();
        var audit =
                SixMaxAlternatingContinuationSolver.audit(
                        game, initial, SixMaxContinuationStudyBudget.standard());
        double target = audit.maximumConditionalGapBb() + 1e-8;
        var result =
                SixMaxAlternatingContinuationSolver.solve(
                        game,
                        initial,
                        new SixMaxAlternatingContinuationSolver.Settings(3, 40, 1, target, 1e-6),
                        SixMaxContinuationStudyBudget.standard(),
                        s -> {},
                        (p, r) -> fail("failed conditional quality persisted"));
        assertSame(initial, result.retainedPolicy());
        assertEquals("CONDITIONAL_TARGET_FAILED", result.report().stopReason());
        assertEquals(1, result.report().rounds().size());
        assertTrue(
                result.report().rounds().getFirst().candidateAudit().maximumConditionalGapBb()
                        > target);
        assertEquals(audit, result.report().retainedAudit());
    }

    @Test
    void rejectedRoundKeepsOriginalPolicyAndNeverCallsPersistence() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var policy =
                SixMaxConnectedPreflopAudit.liftCheckdown(
                        game, SixMaxConditionalPostflopRefinementTest.uniform(game.source()));
        var callbacks = new ArrayList<String>();
        var result =
                SixMaxAlternatingContinuationSolver.solve(
                        game,
                        policy,
                        new SixMaxAlternatingContinuationSolver.Settings(3, 1, 1, 1e6, 1e6),
                        SixMaxContinuationStudyBudget.standard(),
                        s -> {},
                        (p, r) -> callbacks.add(r.retainedSolutionHash()));
        assertSame(policy, result.retainedPolicy());
        assertEquals(0, result.report().acceptedRounds());
        assertEquals(1, result.report().rounds().size());
        assertTrue(callbacks.isEmpty());
        assertEquals("NO_MATERIAL_IMPROVEMENT", result.report().stopReason());
        assertEquals(result.report().initialAudit(), result.report().retainedAudit());
        assertEquals(
                result.report().initialAudit().solutionHash(),
                result.report().rounds().getFirst().retainedSolutionHash());
    }

    @Test
    void acceptedRoundsChainFromLastRetainedPolicyAndPersistOnlyPassingCandidates() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var policy =
                SixMaxConnectedPreflopAudit.liftCheckdown(
                        game, SixMaxConditionalPostflopRefinementTest.uniform(game.source()));
        var persisted = new ArrayList<String>();
        var result =
                SixMaxAlternatingContinuationSolver.solve(
                        game,
                        policy,
                        new SixMaxAlternatingContinuationSolver.Settings(2, 40, 10, 1e6, 1e-6),
                        SixMaxContinuationStudyBudget.standard(),
                        s -> {},
                        (p, r) -> {
                            assertTrue(r.decision().accepted());
                            assertEquals(
                                    SixMaxConnectedPostflopAudit.solutionHash(p),
                                    r.retainedSolutionHash());
                            persisted.add(r.retainedSolutionHash());
                        });
        assertTrue(result.report().acceptedRounds() >= 1);
        assertEquals(result.report().acceptedRounds(), persisted.size());
        var current = result.report().initialAudit();
        for (var round : result.report().rounds()) {
            assertEquals(current.solutionHash(), round.acceptedInputHash());
            assertEquals(
                    current.parentQuality().nashConvBb()
                            - round.candidateAudit().parentQuality().nashConvBb(),
                    round.decision().parentImprovementBb(),
                    1e-12);
            if (round.decision().accepted()) current = round.candidateAudit();
            assertEquals(current.solutionHash(), round.retainedSolutionHash());
        }
        assertEquals(current, result.report().retainedAudit());
        assertEquals(
                current.solutionHash(),
                SixMaxConnectedPostflopAudit.solutionHash(result.retainedPolicy()));
        assertEquals(
                current,
                SixMaxAlternatingContinuationSolver.audit(
                        game, result.retainedPolicy(), SixMaxContinuationStudyBudget.standard()));
    }

    @Test
    void refusesIncompleteOrUnqualifiedBaselineBeforeAnyTraining() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var settings = new SixMaxAlternatingContinuationSolver.Settings(1, 1, 1, 1e-10, 1e-6);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxAlternatingContinuationSolver.solve(
                                game,
                                new CfrSolution(1, Map.of()),
                                settings,
                                SixMaxContinuationStudyBudget.standard(),
                                s -> fail("training started"),
                                (p, r) -> fail("persisted")));
        var policy =
                SixMaxConnectedPreflopAudit.liftCheckdown(
                        game, SixMaxConditionalPostflopRefinementTest.uniform(game.source()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxAlternatingContinuationSolver.solve(
                                game,
                                policy,
                                settings,
                                SixMaxContinuationStudyBudget.standard(),
                                s -> fail("training started"),
                                (p, r) -> fail("persisted")));
    }
}
