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

    @Test
    void misspecifiedObservationsKeepGeneratingDealsAndPhysicalOracleFixed() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var truth = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var correct = PhysicalActionBeliefAudit.assess(2_000, 5, 43, profile, truth, truth);
        var uniform =
                PhysicalActionBeliefAudit.assess(
                        2_000,
                        5,
                        43,
                        profile,
                        truth,
                        ButtonBigBlindRangeValidationFixture.assumedActionBelief(
                                profile,
                                ButtonBigBlindRangeValidationFixture.ActionBeliefAssumption
                                        .UNINFORMATIVE));
        var reversed =
                PhysicalActionBeliefAudit.assess(
                        2_000,
                        5,
                        43,
                        profile,
                        truth,
                        ButtonBigBlindRangeValidationFixture.assumedActionBelief(
                                profile,
                                ButtonBigBlindRangeValidationFixture.ActionBeliefAssumption
                                        .REVERSED));
        assertEquals(correct.staticRange(), uniform.staticRange());
        assertEquals(correct.staticRange(), reversed.staticRange());
        assertEquals(correct.generatingBeliefHash(), uniform.generatingBeliefHash());
        assertNotEquals(correct.assumedBeliefHash(), reversed.assumedBeliefHash());
        assertEquals(correct.staticRange(), uniform.actionConditioned());
        assertEquals(0, uniform.conditionedMinusStatic().conditionedMinusStaticBb(), 1e-12);
        assertEquals(
                correct.actionConditioned().physicalOracleGainBb(),
                reversed.actionConditioned().physicalOracleGainBb());
        assertNotEquals(
                ButtonBigBlindRangeValidationFixture.createActionBucketed(profile, truth)
                        .contentHash(),
                ButtonBigBlindRangeValidationFixture.createActionBucketed(
                                profile,
                                ButtonBigBlindRangeValidationFixture.assumedActionBelief(
                                        profile,
                                        ButtonBigBlindRangeValidationFixture.ActionBeliefAssumption
                                                .REVERSED))
                        .contentHash());
    }

    @Test
    void handDependentRiverResponseUsesTheSamePreflopPosterior() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var game = ButtonBigBlindRangeValidationFixture.createActionBucketed(profile);
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
        var uniform =
                ButtonBigBlindRangeValidationFixture.assumedActionBelief(
                        profile,
                        ButtonBigBlindRangeValidationFixture.ActionBeliefAssumption.UNINFORMATIVE);
        assertEquals(
                PhysicalRiverPairCallResponse.betIncrement(state, deals, game.potBb() / 2, 8),
                PhysicalActionBeliefAudit.exactPairCallBetIncrement(
                        state, deals, uniform, game.potBb() / 2, 8),
                1e-12);
        var truth = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var call =
                PhysicalActionBeliefAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        truth,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL);
        var pair =
                PhysicalActionBeliefAudit.assess(
                        2_000,
                        5,
                        42,
                        profile,
                        truth,
                        truth,
                        PhysicalActionBeliefAudit.ResponseModel.PAIR_OR_BETTER_CALL);
        assertEquals(
                call.actionConditioned().heldOutBoards(), pair.actionConditioned().heldOutBoards());
        assertEquals(
                call.actionConditioned().discoveredBuckets(),
                pair.actionConditioned().discoveredBuckets());
        assertNotEquals(
                call.actionConditioned().physicalOracleGainBb(),
                pair.actionConditioned().physicalOracleGainBb());
    }
}
