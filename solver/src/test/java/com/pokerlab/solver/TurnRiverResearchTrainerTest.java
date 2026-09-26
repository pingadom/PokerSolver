package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TurnRiverResearchTrainerTest {
    private static TurnRiverResearchTrainer trainer;

    @BeforeAll
    static void load() throws Exception {
        try (var resource =
                TurnRiverResearchTrainerTest.class.getResourceAsStream(
                        "/turn-river-validation-pack.json")) {
            assertNotNull(resource);
            trainer =
                    new TurnRiverResearchTrainer(
                            TurnRiverPackJson.read(
                                    new String(resource.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test
    void samplesBothStreetsAndGradesOnlyFromPack() {
        assertTrue(trainer.availableTurnQuestions() > 0);
        assertTrue(trainer.availableRiverQuestions() > 0);
        boolean sawTurn = false;
        boolean sawRiver = false;
        for (long seed = 0; seed < 20; seed++) {
            TurnRiverResearchTrainer.Question question = trainer.question(seed);
            assertEquals(question, trainer.question(seed));
            assertEquals("VALIDATION_ONLY", question.publicationStatus());
            assertEquals(2, question.legalActions().size());
            assertEquals(trainer.packHash(), question.packHash());
            if (question.street().equals("TURN")) {
                sawTurn = true;
                assertNull(question.river());
            } else {
                sawRiver = true;
                assertNotNull(question.river());
            }
            String currentHistory =
                    question.street().equals("TURN")
                            ? question.turnHistory()
                            : question.riverHistory();
            double calledTurn =
                    question.street().equals("RIVER") && !question.turnHistory().equals("kk")
                            ? 10
                            : 0;
            double outstanding = currentHistory.equals("b") || currentHistory.equals("kb") ? 10 : 0;
            assertEquals(20 + 2 * calledTurn + outstanding, question.potBb());
            assertEquals(80 - calledTurn, question.remainingStackBb());
            String action = question.legalActions().getFirst();
            TurnRiverResearchTrainer.Feedback feedback =
                    trainer.grade(seed, trainer.packHash(), action);
            assertEquals(action, feedback.selectedAction());
            assertTrue(feedback.evLossBb() >= 0);
            assertEquals(
                    0, feedback.bestEvBb() - feedback.selectedEvBb() - feedback.evLossBb(), 1e-9);
        }
        assertTrue(sawTurn && sawRiver);
    }

    @Test
    void refusesStalePackAndIllegalAction() {
        TurnRiverResearchTrainer.Question question = trainer.question(42);
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(42, "0".repeat(64), question.legalActions().getFirst()));
        String illegal = question.legalActions().contains("c") ? "b" : "c";
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(42, trainer.packHash(), illegal));
    }
}
