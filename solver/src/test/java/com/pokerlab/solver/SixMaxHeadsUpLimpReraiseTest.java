package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.*;
import org.junit.jupiter.api.*;

class SixMaxHeadsUpLimpReraiseTest {
    static SixMaxHeadsUpPreflopStudy.Result study;
    static SixMaxPreflopSolutionPack source;

    @BeforeAll
    static void replay() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
        study =
                SixMaxHeadsUpPreflopStudy.replay(
                        SixMaxHeadsUpPreflopStudyTest.data("limp-reraise", "policy"),
                        SixMaxHeadsUpPreflopStudyTest.data("limp-reraise", "report"),
                        source);
    }

    @AfterAll
    static void release() {
        study = null;
        source = null;
    }

    @Test
    void separatePublicHistoryPassesEveryExistingMaterialDecisionGate() throws Exception {
        var r = study.report();
        assertTrue(r.accepted());
        assertFalse(r.trainerAdmission());
        assertEquals("VALIDATION_ONLY", r.publicationStatus());
        assertEquals(4, r.materialDecisions());
        assertEquals(4, r.stableDecisions());
        assertEquals(73, r.binding().completeTreeStates());
        assertEquals(.0006611382850751814, r.binding().historyReach(), 1e-15);
        assertEquals(9, r.binding().specification().history().size());
        assertTrue(
                r.decisions().stream()
                        .allMatch(d -> d.material() && d.stable() && d.comparisons().size() == 2));
        assertTrue(
                r.decisions().stream()
                        .flatMap(d -> d.comparisons().stream())
                        .allMatch(c -> c.failures().isEmpty()));
        var existing =
                SixMaxHeadsUpPreflopStudy.solve(
                        source,
                        SixMaxHeadsUpPreflopStudyTest.specification("three-nine-twentytwo"));
        assertEquals(existing.report().binding().sourcePackHash(), r.binding().sourcePackHash());
        assertNotEquals(existing.report().binding().posteriorHash(), r.binding().posteriorHash());
        assertNotEquals(existing.report().solve().snapshotHash(), r.solve().snapshotHash());
    }

    @Test
    void tableShowsTheActualLimpReraiseAndBothFoldedCallersDeadMoney() throws Exception {
        var trainer = new SixMaxHeadsUpPreflopTrainer(study);
        var seen = new HashSet<String>();
        for (long seed = 0; seed < 64; seed++) {
            var q = trainer.question(seed);
            seen.add(q.hero() + q.heroCards());
            assertEquals(6, q.table().players().size());
            assertEquals(q.hero(), q.table().actingSeat());
            assertTrue(q.history().size() >= 9);
            assertFalse(q.trainerAdmission());
            assertEquals(
                    study.report().binding().specification().history(), q.history().subList(0, 9));
            assertEquals(
                    4,
                    q.table().players().stream()
                            .filter(p -> p.status() == SixMaxPreflopPublicTable.PlayerStatus.FOLDED)
                            .count());
            for (var player : q.table().players())
                if (player.seat() == Seat.BTN || player.seat() == Seat.SB) {
                    assertEquals(1, player.committedBb(), 1e-12);
                    assertEquals(SixMaxPreflopBetting.Kind.FOLD, player.lastAction().kind());
                }
            if (q.hero() == Seat.BB) {
                assertEquals(14, q.table().potBb(), 1e-12);
                assertEquals(6, q.table().toCallBb(), 1e-12);
                assertEquals(List.of("fold", "call", "raise:22.0"), q.legalActions());
            } else {
                assertEquals(Seat.CO, q.hero());
                assertEquals(33, q.table().potBb(), 1e-12);
                assertEquals(13, q.table().toCallBb(), 1e-12);
            }
        }
        assertEquals(4, seen.size());
    }

    @Test
    void everyCandidateAndReferenceActionEvMatchesIndependentLiteralContinuationPlans() {
        var game = study.core();
        var candidate = study.artifact().orElseThrow().solution();
        var policies = new ArrayList<CfrSolution>();
        policies.add(candidate);
        for (int t : SixMaxHeadsUpPreflopStudy.REFERENCE_BUDGETS)
            policies.add(
                    new MultiPlayerCfrSolver<>(
                                    game,
                                    CfrSolver.Variant.CFR_PLUS,
                                    MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY)
                            .solve(t));
        for (var d : SixMaxHeadsUpPreflopDecisionValues.assess(game, candidate))
            for (var policy : policies) {
                var expected = SixMaxHeadsUpPreflopStudyTest.pureBest(game, d.roots(), policy);
                var values = SixMaxHeadsUpPreflopDecisionValues.values(game, d.roots(), policy);
                for (String action : expected.keySet())
                    assertEquals(expected.get(action), values.actionEvBb().get(action), 1e-10);
                assertEquals(0, values.actionEvBb().get("fold"), 1e-12);
            }
    }

    @Test
    void deterministicSessionsGradeAllChoicesAndRemainBoundToThisStudy() throws Exception {
        var trainer = new SixMaxHeadsUpPreflopTrainer(study);
        var actions = new ArrayList<String>();
        for (int index = 0; index < 10; index++) {
            var q = trainer.sessionQuestion(711, index);
            assertEquals(q, trainer.sessionQuestion(711, index));
            var feedback = trainer.grade(q, q.legalActions().getFirst());
            String best =
                    feedback.values().actionEvBb().entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .orElseThrow()
                            .getKey();
            actions.add(best);
            for (String action : q.legalActions()) {
                var graded = trainer.grade(q, action);
                assertEquals(
                        feedback.values().actionEvBb().get(action),
                        graded.selectedActionEvBb(),
                        1e-12);
                assertFalse(graded.trainerAdmission());
            }
        }
        var review = trainer.review(711, trainer.studyHash(), actions);
        assertEquals(10, review.attempts().size());
        assertEquals(0, review.totalEvLossBb(), 1e-12);
        var other =
                new SixMaxHeadsUpPreflopTrainer(
                        SixMaxHeadsUpPreflopStudy.solve(
                                source, SixMaxHeadsUpPreflopStudyTest.specification("three-nine")));
        assertThrows(
                IllegalArgumentException.class, () -> other.grade(trainer.question(711), "fold"));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(711, other.studyHash(), actions));
    }
}
