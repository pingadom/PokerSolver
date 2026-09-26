package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FlopTurnRiverDecisionEvaluatorTest {
    private static FlopTurnRiverDecisionEvaluator evaluator;

    @BeforeAll
    static void prepare() {
        FlopTurnRiverSpot fixture = FlopTurnRiverValidationSpot.create().withFullTurnDeck();
        FlopTurnRiverSpot small =
                new FlopTurnRiverSpot(
                        fixture.flop(),
                        fixture.potBb(),
                        fixture.remainingStackBb(),
                        fixture.flopBetBb(),
                        fixture.turnBetBb(),
                        fixture.riverBetBb(),
                        List.of(
                                fixture.firstRange().stream()
                                        .filter(combo -> combo.key().equals("Ah As"))
                                        .findFirst()
                                        .orElseThrow()),
                        List.of(fixture.secondRange().getFirst()),
                        fixture.turnCandidates());
        evaluator =
                new FlopTurnRiverDecisionEvaluator(
                        FlopTurnRiverPackBuilder.generate(small, 3, "2026-09-26T12:00:00Z", 100));
    }

    @Test
    void gradesConnectedFlopTurnAndRiverDecisions() {
        var flop = evaluator.evaluate(0, "", null, "", null, "", "Ah As");
        assertEquals(List.of("b", "k"), flop.actionEvBb().keySet().stream().sorted().toList());
        assertEquals(
                1,
                flop.actionFrequency().values().stream().mapToDouble(Double::doubleValue).sum(),
                1e-12);
        assertEquals(0, Math.min(flop.evLossBb("b"), flop.evLossBb("k")), 1e-12);
        assertTrue(Double.isFinite(flop.actionEvBb().get("b")));

        var turn = evaluator.evaluate(0, "kk", Card.parse("4h"), "", null, "", "Ah As");
        assertEquals(flop.packHash(), turn.packHash());
        assertEquals(Card.parse("4h"), turn.turn());
        assertEquals(2, turn.actionEvBb().size());

        var river =
                evaluator.evaluate(0, "kk", Card.parse("4h"), "kk", Card.parse("9c"), "", "Ah As");
        assertEquals(flop.packHash(), river.packHash());
        assertEquals(Card.parse("9c"), river.river());
        assertEquals(2, river.actionEvBb().size());
    }

    @Test
    void rejectsIllegalHistoriesBlockedCardsAndActions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(0, "b", Card.parse("4h"), "", null, "", "Ah As"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(0, "kk", null, "k", null, "", "Ah As"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        evaluator.evaluate(
                                0, "kk", Card.parse("4h"), "kk", Card.parse("Ah"), "", "Ah As"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(0, "", null, "", null, "", "7s 6s"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(0, "", null, "", null, "", "Ah As").evLossBb("c"));
    }
}
