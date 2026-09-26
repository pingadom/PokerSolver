package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class RiverResearchTrainerTest {
    private static RiverResearchTrainer trainer() throws Exception {
        try (var resource =
                RiverResearchTrainerTest.class.getResourceAsStream("/river-validation-pack.json")) {
            assertNotNull(resource);
            return new RiverResearchTrainer(
                    RiverPackJson.read(
                            new String(resource.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test
    void samplesAllFourDecisionHistoriesAndGradesFromSavedPack() throws Exception {
        RiverResearchTrainer trainer = trainer();
        assertEquals(12, trainer.availableQuestions());
        Set<String> histories =
                LongStream.range(0, 200)
                        .mapToObj(trainer::question)
                        .map(RiverResearchTrainer.Question::publicHistory)
                        .collect(Collectors.toSet());
        assertEquals(Set.of("", "k", "b", "kb"), histories);
        for (long seed = 0; seed < 30; seed++) {
            RiverResearchTrainer.Question question = trainer.question(seed);
            assertEquals(question, trainer.question(seed));
            assertEquals(RiverSolutionPack.VALIDATION_ONLY, question.publicationStatus());
            assertEquals(5, question.board().size());
            assertEquals(trainer.packHash(), question.packHash());
            for (String action : question.legalActions()) {
                RiverResearchTrainer.Feedback feedback =
                        trainer.grade(seed, trainer.packHash(), action);
                assertEquals(question.heroCombo(), feedback.heroCombo());
                assertEquals(question.publicHistory(), feedback.publicHistory());
                assertEquals(action, feedback.selectedAction());
                assertEquals(feedback.actionEvBb().get(action), feedback.selectedEvBb());
                assertEquals(
                        feedback.bestEvBb() - feedback.selectedEvBb(), feedback.evLossBb(), 1e-9);
                assertEquals(question.legalActions().size(), feedback.actionEvBb().size());
            }
        }
    }

    @Test
    void rejectsStalePackAndActionOutsideCurrentNode() throws Exception {
        RiverResearchTrainer trainer = trainer();
        RiverResearchTrainer.Question question = trainer.question(42);
        String illegal = question.legalActions().contains("c") ? "b" : "c";
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(42, "0".repeat(64), question.legalActions().get(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(42, trainer.packHash(), illegal));
    }
}
