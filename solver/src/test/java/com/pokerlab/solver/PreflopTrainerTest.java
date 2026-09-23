package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreflopTrainerTest {
    private static PreflopTrainer trainer() throws Exception {
        try (var resource = PreflopTrainerTest.class.getResourceAsStream("/validation-pack.json")) {
            assertNotNull(resource);
            return new PreflopTrainer(
                    PreflopPackJson.read(
                            new String(resource.readAllBytes(), StandardCharsets.UTF_8)));
        }
    }

    @Test
    void samplesBlockerAdjustedHeroMarginalDeterministically() throws Exception {
        PreflopTrainer trainer = trainer();
        Map<String, Double> probabilities = trainer.heroDealProbabilities();
        assertEquals(3.0 / 13, probabilities.get("Ad Kd"), 1e-12);
        assertEquals(4.0 / 13, probabilities.get("Ah As"), 1e-12);
        assertEquals(6.0 / 13, probabilities.get("Kh Ks"), 1e-12);
        assertEquals(
                1, probabilities.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        assertEquals(trainer.question(42), trainer.question(42));
        assertThrows(UnsupportedOperationException.class, () -> probabilities.clear());
    }

    @Test
    void questionContainsPublicHistoryButNoOpponentHandOrSolution() throws Exception {
        PreflopTrainer.Question question = trainer().question(42);
        assertEquals("utg-versus-button-five-bet", question.spotId());
        assertEquals(PreflopSolutionPack.VALIDATION_ONLY, question.publicationStatus());
        assertEquals(PreflopAllInSpot.Seat.UTG, question.heroSeat());
        assertEquals(PreflopAllInSpot.Seat.BTN, question.opponentSeat());
        assertEquals("NO_RAKE", question.rakeModel());
        assertEquals(PreflopSolutionPack.EXACT_ENUMERATION, question.payoffMethod());
        assertEquals(0.011634940445212294, question.estimatedGameGapBb(), 1e-12);
        assertEquals(0, question.maximumCalledPayoffStandardErrorBb());
        assertEquals(63.5, question.potBb());
        assertEquals(22, question.heroCommittedBb());
        assertEquals(40, question.opponentCommittedBb());
        assertEquals(10, question.priorActions().size());
        assertEquals(
                java.util.List.of(PreflopTrainer.Action.SHOVE, PreflopTrainer.Action.FOLD),
                question.legalActions());
        String json = new ObjectMapper().writeValueAsString(question);
        assertFalse(json.contains("secondRange"));
        assertFalse(json.contains("As Kc"));
        assertFalse(json.contains("Jh Js"));
        assertFalse(json.contains("Qc Qd"));
        assertFalse(json.contains("shoveEvBb"));
        assertFalse(json.contains("foldEvBb"));
        assertFalse(json.contains("shoveFrequency"));
    }

    @Test
    void gradesActionEvAgainstBestAvailableActionAndRejectsWrongSpot() throws Exception {
        PreflopTrainer trainer = trainer();
        PreflopTrainer.Question question = trainer.question(42);
        PreflopTrainer.Feedback fold = trainer.grade(question, PreflopTrainer.Action.FOLD);
        PreflopTrainer.Feedback shove = trainer.grade(question, PreflopTrainer.Action.SHOVE);
        assertEquals(-22, fold.selectedEvBb());
        assertEquals(shove.shoveEvBb(), shove.selectedEvBb());
        assertEquals(shove.selectedEvBb(), fold.bestEvBb());
        assertEquals(shove.selectedEvBb() - fold.selectedEvBb(), fold.evLossBb(), 1e-12);
        assertEquals(0, shove.evLossBb());
        assertEquals(1, fold.shoveFrequency() + fold.foldFrequency(), 1e-12);
        assertEquals(question.spotHash(), fold.spotHash());
        assertEquals(question.heroCombo(), fold.heroCombo());

        PreflopTrainer.Question wrongHash = with(question, "wrong-hash", question.heroCombo());
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(wrongHash, PreflopTrainer.Action.FOLD));
        PreflopTrainer.Question unknownCombo = with(question, question.spotHash(), "2c 3d");
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.grade(unknownCombo, PreflopTrainer.Action.FOLD));
        assertThrows(NullPointerException.class, () -> trainer.grade(question, null));
    }

    private static PreflopTrainer.Question with(
            PreflopTrainer.Question original, String spotHash, String heroCombo) {
        return new PreflopTrainer.Question(
                original.spotId(),
                spotHash,
                original.publicationStatus(),
                original.heroSeat(),
                original.opponentSeat(),
                heroCombo,
                original.effectiveStackBb(),
                original.smallBlindBb(),
                original.rakeModel(),
                original.payoffMethod(),
                original.estimatedGameGapBb(),
                original.maximumCalledPayoffStandardErrorBb(),
                original.potBb(),
                original.heroCommittedBb(),
                original.opponentCommittedBb(),
                original.priorActions(),
                original.legalActions());
    }
}
