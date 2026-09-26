package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class MultiwayCallTrainerTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    @Test
    void gradesConditionalEvAcrossHiddenOpponentDeals() {
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        List.of(PreflopAllInSpot.Seat.UTG, PreflopAllInSpot.Seat.BB),
                        List.of(
                                List.of(combo("AS", "AH", 1), combo("KS", "KH", 3)),
                                List.of(combo("QC", "QD", 1))),
                        List.of(10.0, 1.0),
                        10,
                        0,
                        (dealt, mask) ->
                                MultiwayShowdownEstimate.certain(
                                        dealt.get(0).first().compact().startsWith("A")
                                                ? new double[] {0, 1}
                                                : new double[] {1, 0}));
        CfrSolution solution =
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(100);
        MultiwayCallTrainer trainer = new MultiwayCallTrainer(game, solution);
        var first = trainer.question(42, 1);
        var second = trainer.question(99, 1);
        assertEquals("Qc Qd", first.heroCombo());
        assertEquals("VALIDATION_ONLY", first.publicationStatus());
        assertEquals(List.of(), first.priorResponses());
        assertEquals(11, first.potBb());
        assertEquals(9, first.callCostBb());
        assertEquals(
                List.of(MultiwayCallTrainer.Action.CALL, MultiwayCallTrainer.Action.FOLD),
                first.legalActions());
        var feedback = trainer.grade(first, MultiwayCallTrainer.Action.CALL);
        assertEquals(-5, feedback.callEvBb(), 1e-9);
        assertEquals(-1, feedback.foldEvBb(), 1e-9);
        assertEquals(4, feedback.evLossBb(), 1e-9);
        assertEquals(
                feedback.callEvBb(),
                trainer.grade(second, MultiwayCallTrainer.Action.CALL).callEvBb());
        assertEquals(0, trainer.grade(first, MultiwayCallTrainer.Action.FOLD).evLossBb());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        trainer.grade(
                                new MultiwayCallTrainer.Question(
                                        first.seed(),
                                        first.actingPlayer(),
                                        first.actingSeat(),
                                        "As Ah",
                                        first.priorResponses(),
                                        first.potBb(),
                                        first.callCostBb(),
                                        first.stackBb(),
                                        first.maximumPayoffStandardErrorBb(),
                                        first.nashConvBb(),
                                        first.publicationStatus(),
                                        first.legalActions()),
                                MultiwayCallTrainer.Action.CALL));
        assertThrows(IllegalArgumentException.class, () -> trainer.question(42, 0));
    }

    @Test
    void sixSeatQuestionContainsOnlyPublicHistoryAndActingHand() {
        List<List<WeightedCombo>> ranges =
                List.of(
                        List.of(combo("AS", "AH", 1)),
                        List.of(combo("KS", "KH", 1)),
                        List.of(combo("QS", "QH", 1)),
                        List.of(combo("JS", "JH", 1)),
                        List.of(combo("TS", "TH", 1)),
                        List.of(combo("9S", "9H", 1)));
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        List.of(PreflopAllInSpot.Seat.values()),
                        ranges,
                        List.of(10.0, 1.0, 2.0, 0.0, 0.5, 1.0),
                        10,
                        0,
                        (dealt, mask) ->
                                MultiwayShowdownEstimate.certain(new double[] {1, 0, 0, 0, 0, 0}));
        CfrSolution solution =
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(100);
        MultiwayCallTrainer trainer = new MultiwayCallTrainer(game, solution);
        var question = trainer.question(42, 5);
        assertEquals(PreflopAllInSpot.Seat.BB, question.actingSeat());
        assertEquals("9h 9s", question.heroCombo());
        assertEquals(4, question.priorResponses().size());
        assertTrue(question.potBb() >= 14.5);
        assertEquals(9, question.callCostBb());
        assertEquals(question, trainer.question(42, 5));
        assertTrue(trainer.grade(question, MultiwayCallTrainer.Action.CALL).evLossBb() >= 9);
    }

    @Test
    void priorPublicActionChangesTheConditionalHiddenRange() {
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        List.of(
                                PreflopAllInSpot.Seat.UTG,
                                PreflopAllInSpot.Seat.BTN,
                                PreflopAllInSpot.Seat.BB),
                        List.of(
                                List.of(combo("AS", "AH", 1)),
                                List.of(combo("KS", "KH", 1), combo("QS", "QH", 1)),
                                List.of(combo("JC", "JD", 1))),
                        List.of(10.0, 1.0, 1.0),
                        10,
                        0,
                        (dealt, mask) -> {
                            double[] shares = new double[3];
                            if (dealt.get(1).first().compact().startsWith("K"))
                                shares[(mask & 0b010) != 0 ? 1 : 0] = 1;
                            else shares[(mask & 0b100) != 0 ? 2 : 0] = 1;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        CfrSolution solution =
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(300);
        MultiwayCallTrainer trainer = new MultiwayCallTrainer(game, solution);
        MultiwayCallTrainer.Question afterCall = null;
        MultiwayCallTrainer.Question afterFold = null;
        for (long seed = 0; seed < 100 && (afterCall == null || afterFold == null); seed++) {
            var question = trainer.question(seed, 2);
            if (question.priorResponses().get(0).action() == MultiwayCallTrainer.Action.CALL)
                afterCall = question;
            else afterFold = question;
        }
        assertTrue(afterCall != null && afterFold != null);
        double callAfterCall = trainer.grade(afterCall, MultiwayCallTrainer.Action.CALL).callEvBb();
        double callAfterFold = trainer.grade(afterFold, MultiwayCallTrainer.Action.CALL).callEvBb();
        assertTrue(callAfterFold - callAfterCall > 15);
    }
}
