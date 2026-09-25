package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class TurnRiverDecisionEvaluatorTest {
    private static TurnRiverSolutionPack pack;
    private static TurnRiverDecisionEvaluator evaluator;

    @BeforeAll
    static void buildPack() {
        pack =
                TurnRiverPackBuilder.generate(
                        TurnRiverValidationSpot.create(), 100, "2026-09-25T12:00:00Z");
        evaluator = new TurnRiverDecisionEvaluator(pack);
    }

    @Test
    void gradesBothStreetsWithoutOpponentCards() {
        TurnRiverDecisionEvaluator.Decision turn = evaluator.evaluate(0, "", null, "", "Ah As");
        assertEquals(Set.of("k", "b"), turn.actionFrequency().keySet());
        assertEquals(
                1,
                turn.actionFrequency().values().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
        assertTrue(Double.isFinite(turn.actionEvBb().get("k")));
        assertTrue(Double.isFinite(turn.actionEvBb().get("b")));
        assertEquals(0, Math.min(turn.evLossBb("k"), turn.evLossBb("b")), 1e-12);
        TurnRiverDecisionEvaluator.Decision river =
                evaluator.evaluate(0, "kk", Card.parse("9c"), "kb", "Ah As");
        assertEquals(Set.of("c", "f"), river.actionFrequency().keySet());
        assertEquals(turn.packHash(), river.packHash());
        assertThrows(IllegalArgumentException.class, () -> river.evLossBb("b"));
    }

    @Test
    void riverPosteriorWeightsObservedOpponentActionsAndCardBlockers() {
        TurnRiverGame game = pack.spot().game();
        double denominator = 0;
        double callTotal = 0;
        double foldTotal = 0;
        Card river = Card.parse("9c");
        for (ChanceOutcome<TurnRiverGame.State> outcome :
                game.chanceOutcomes(game.initialState())) {
            TurnRiverGame.State deal = outcome.state();
            if (!deal.first().key().equals("Ah As")) continue;
            TurnRiverGame.State firstCheck = game.afterAction(deal, "k");
            double secondTurnCheck =
                    pack.solution().at(1, game.informationSet(firstCheck)).get("k");
            TurnRiverGame.State checkedTurn = game.afterAction(firstCheck, "k");
            for (ChanceOutcome<TurnRiverGame.State> draw : game.chanceOutcomes(checkedTurn)) {
                if (!draw.state().river().equals(river)) continue;
                TurnRiverGame.State firstRiverCheck = game.afterAction(draw.state(), "k");
                double secondRiverBet =
                        pack.solution().at(1, game.informationSet(firstRiverCheck)).get("b");
                TurnRiverGame.State facingBet = game.afterAction(firstRiverCheck, "b");
                double weight =
                        outcome.probability()
                                * secondTurnCheck
                                * draw.probability()
                                * secondRiverBet;
                denominator += weight;
                callTotal += weight * game.terminalUtility(game.afterAction(facingBet, "c"));
                foldTotal += weight * game.terminalUtility(game.afterAction(facingBet, "f"));
            }
        }
        assertTrue(denominator > 0);
        TurnRiverDecisionEvaluator.Decision decision =
                evaluator.evaluate(0, "kk", river, "kb", "Ah As");
        assertEquals(callTotal / denominator, decision.actionEvBb().get("c"), 1e-9);
        assertEquals(foldTotal / denominator, decision.actionEvBb().get("f"), 1e-9);
    }

    @Test
    void rejectsImpossibleOrMisstatedDecisions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(0, "", Card.parse("9c"), "", "Ah As"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(0, "kk", Card.parse("As"), "", "Ah As"));
        assertThrows(
                IllegalArgumentException.class, () -> evaluator.evaluate(1, "", null, "", "Kc Kd"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evaluator.evaluate(0, "", null, "b", "Ah As"));
    }
}
