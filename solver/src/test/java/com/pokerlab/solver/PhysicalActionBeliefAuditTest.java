package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicalActionBeliefAuditTest {
    @Test
    void independentlyWeightedPhysicalDealsMatchPublicPosterior() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var game = ButtonBigBlindRangeValidationFixture.createActionBucketed(profile);
        var belief = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var deals = game.chanceOutcomes(game.initialState());
        var state =
                new ButtonBigBlindPhysicalDeckGame.State(
                        deals.getFirst().state().bigBlind(),
                        deals.getFirst().state().button(),
                        "oc",
                        List.of(Card.parse("2c"), Card.parse("3d"), Card.parse("4h")),
                        "kk",
                        Card.parse("5s"),
                        "kk",
                        Card.parse("9c"),
                        "");
        var button = deals.stream().map(deal -> deal.state().button()).distinct().toList();
        var board =
                List.of(
                        Card.parse("2c"),
                        Card.parse("3d"),
                        Card.parse("4h"),
                        Card.parse("5s"),
                        Card.parse("9c"));
        assertEquals(
                PhysicalActionBeliefAudit.exactPosteriorMargin(state, deals, belief),
                PublicRiverEquityBucket.showdownMargin(
                        board,
                        state.bigBlind(),
                        belief.posteriorWeights(
                                button, PreflopActionBelief.ObservedAction.BUTTON_OPEN)),
                1e-12);
    }

    @Test
    void sampleSplitUsesIdenticalHeldOutBoardsAndIsRepeatable() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        var report = PhysicalActionBeliefAudit.assess(2_000, 5, 42, profile);
        assertEquals(report, PhysicalActionBeliefAudit.assess(2_000, 5, 42, profile));
        assertEquals(1_000, report.staticRange().heldOutBoards());
        assertEquals(
                report.staticRange().heldOutBoards(), report.actionConditioned().heldOutBoards());
        assertEquals(
                report.staticRange().physicalOracleGainBb(),
                report.actionConditioned().physicalOracleGainBb());
        assertEquals(
                report.actionConditioned().selectedGainBb() - report.staticRange().selectedGainBb(),
                report.conditionedMinusStatic().conditionedMinusStaticBb(),
                1e-9);
        assertTrue(report.conditionedMinusStatic().standardErrorBb() >= 0);
        assertThrows(
                IllegalArgumentException.class,
                () -> PhysicalActionBeliefAudit.assess(3, 1, 42, profile));
    }
}
