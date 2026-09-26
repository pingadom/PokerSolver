package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RiverDecisionEvaluatorTest {
    private static RiverSolutionPack fixture() throws Exception {
        try (var resource =
                RiverDecisionEvaluatorTest.class.getResourceAsStream(
                        "/river-validation-pack.json")) {
            assertNotNull(resource);
            return RiverPackJson.read(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void gradesBothActionsFromSavedPolicyWithoutExposingOpponentCards() throws Exception {
        RiverSolutionPack pack = fixture();
        RiverDecisionEvaluator evaluator = new RiverDecisionEvaluator(pack);
        RiverDecisionEvaluator.Decision root = evaluator.evaluate(0, "", "Ah As");
        assertEquals(RiverPackJson.contentHash(pack), root.packHash());
        assertEquals(java.util.Set.of("k", "b"), root.actionEvBb().keySet());
        assertEquals(
                1,
                root.actionFrequency().values().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
        assertEquals(0, Math.min(root.evLossBb("k"), root.evLossBb("b")), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> root.evLossBb("c"));

        RiverDecisionEvaluator.Decision response = evaluator.evaluate(0, "kb", "Ah As");
        assertEquals(java.util.Set.of("c", "f"), response.actionEvBb().keySet());
        assertTrue(response.actionEvBb().get("c") > response.actionEvBb().get("f"));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(0, "b", "Ah As"));
    }

    @Test
    void opponentRangeIsConditionedOnTheObservedBet() throws Exception {
        RiverSolutionPack pack = fixture();
        RiverBetGame game = pack.spot().game();
        RiverDecisionEvaluator.Decision decision =
                new RiverDecisionEvaluator(pack).evaluate(1, "b", "Kc Kd");
        double reach = 0;
        double call = 0;
        double fold = 0;
        for (ChanceOutcome<RiverBetGame.State> outcome : game.chanceOutcomes(game.initialState())) {
            RiverBetGame.State deal = outcome.state();
            if (!deal.second().key().equals("Kc Kd")) continue;
            double weight =
                    outcome.probability()
                            * pack.solution().at(0, game.informationSet(deal)).get("b");
            RiverBetGame.State bet = game.afterAction(deal, "b");
            reach += weight;
            call -= weight * game.terminalUtility(game.afterAction(bet, "c"));
            fold -= weight * game.terminalUtility(game.afterAction(bet, "f"));
        }
        assertTrue(reach > 0);
        assertEquals(call / reach, decision.actionEvBb().get("c"), 1e-9);
        assertEquals(fold / reach, decision.actionEvBb().get("f"), 1e-9);
    }
}
