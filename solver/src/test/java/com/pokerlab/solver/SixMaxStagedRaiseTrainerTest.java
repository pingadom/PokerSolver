package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxStagedRaiseTrainerTest {
    @Test
    void trainerQuestionsReplayTheSameScheduleAndGradeEveryLegalThreeBetResponse() {
        var source = SixMaxPreflopPayoffReuseTest.source();
        var spot =
                new SixMaxPreflopResearchSpot(
                        "staged-trainer-test",
                        new SixMaxPreflopBetting.Rules(
                                100,
                                .5,
                                List.of(3.0, 9.0),
                                SixMaxPreflopBetting.RaiseSchedule.NEXT_TARGET),
                        source.spot().ranges(),
                        source.spot().rake(),
                        source.spot().continuationModel());
        var game =
                SixMaxPreflopPayoffReuse.buildExact(
                                source,
                                spot,
                                1,
                                CfrSolver.Variant.CFR_PLUS,
                                SixMaxPreflopPayoffReuseTest.TIME)
                        .pack()
                        .rebuildGame();
        // Explicit complete uniform policy makes rare legal branches observable in this test.
        var policy =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                game, new CfrSolution(1, Map.of()), 160_000)
                        .solution();
        var trainer = new SixMaxPreflopResearchTrainer(game, policy);
        SixMaxPreflopResearchTrainer.Question beforeThreeBet = null;
        boolean sawThreeBet = false;
        for (long seed = 0; seed < 100; seed++) {
            var question = trainer.question(seed);
            var table = SixMaxPreflopPublicTable.replay(spot.rules(), question.priorActions());
            assertEquals(question.actingSeat(), table.actingSeat());
            assertEquals(question.potBb(), table.potBb());
            assertEquals(question.toCallBb(), table.toCallBb());
            assertEquals(6, table.players().size());
            assertEquals("VALIDATION_ONLY", question.publicationStatus());
            long raises =
                    question.priorActions().stream()
                            .filter(action -> action.action().startsWith("raise:"))
                            .count();
            if (raises == 0) {
                assertTrue(question.legalActions().contains("raise:3.0"));
                assertFalse(question.legalActions().contains("raise:9.0"));
            } else if (raises == 1) {
                assertTrue(question.legalActions().contains("raise:9.0"));
                assertFalse(question.legalActions().contains("raise:3.0"));
                beforeThreeBet = question;
            } else {
                assertEquals(2, raises);
                assertFalse(
                        question.legalActions().stream()
                                .anyMatch(action -> action.startsWith("raise:")));
                sawThreeBet = true;
            }
        }
        assertNotNull(beforeThreeBet);
        assertTrue(sawThreeBet);
        for (var action : beforeThreeBet.legalActions()) {
            var feedback = trainer.grade(beforeThreeBet, action);
            assertEquals(beforeThreeBet.legalActions().size(), feedback.actionEvBb().size());
            assertEquals(feedback.actionEvBb().get(action), feedback.selectedEvBb());
            assertEquals(feedback.bestEvBb() - feedback.selectedEvBb(), feedback.evLossBb(), 1e-12);
            assertTrue(feedback.actionEvBb().values().stream().allMatch(Double::isFinite));
            assertTrue(
                    feedback.actionPayoffStandardErrorBb().values().stream()
                            .allMatch(value -> value == 0));
        }
        var captured = beforeThreeBet;
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(captured, "raise:100.0"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopPublicTable.replay(
                                spot.rules(),
                                List.of(
                                        new SixMaxPreflopResearchTrainer.PublicAction(
                                                PreflopAllInSpot.Seat.UTG, "raise:9.0"))));
    }
}
