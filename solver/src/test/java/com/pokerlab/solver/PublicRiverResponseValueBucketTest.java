package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PublicRiverResponseValueBucketTest {
    @Test
    void posteriorValueMatchesIndependentJointDealCalculationForBothResponses() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var preflop = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var postflop = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var game = ButtonBigBlindRangeValidationFixture.createActionBucketed(profile);
        var deals = game.chanceOutcomes(game.initialState());
        var dealt = deals.getFirst().state();
        List<Card> board = cards("2c", "7s", "Th", "3d", "9c");
        var state =
                new ButtonBigBlindPhysicalDeckGame.State(
                        dealt.bigBlind(),
                        dealt.button(),
                        "oc",
                        board.subList(0, 3),
                        "kk",
                        board.get(3),
                        "kk",
                        board.get(4),
                        "");
        var buttonRange = deals.stream().map(deal -> deal.state().button()).distinct().toList();
        var posterior =
                postflop.posteriorWeights(
                        preflop.posteriorWeights(
                                buttonRange, PreflopActionBelief.ObservedAction.BUTTON_OPEN),
                        state,
                        false);
        for (var response : PhysicalActionBeliefAudit.ResponseModel.values()) {
            assertEquals(
                    PhysicalPostflopActionBeliefAudit.exactBetIncrement(
                            state, deals, preflop, postflop, response, game.potBb() / 2, 8),
                    PublicRiverResponseValueBucket.betIncrement(
                            board, state.bigBlind(), posterior, game.potBb() / 2, 8, response),
                    1e-12);
        }
    }

    @Test
    void strategicResponseValueMatchesIndependentJointDealCalculation() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5;
        var preflop = ButtonBigBlindRangeValidationFixture.actionBelief(profile);
        var postflop = ButtonBigBlindRangeValidationFixture.postflopBelief();
        var game = ButtonBigBlindRangeValidationFixture.createActionBucketed(profile);
        var deals = game.chanceOutcomes(game.initialState());
        var dealt = deals.getFirst().state();
        List<Card> board = cards("2c", "7s", "Th", "3d", "9c");
        var state =
                new ButtonBigBlindPhysicalDeckGame.State(
                        dealt.bigBlind(),
                        dealt.button(),
                        "oc",
                        board.subList(0, 3),
                        "kk",
                        board.get(3),
                        "kk",
                        board.get(4),
                        "");
        var buttonRange = deals.stream().map(deal -> deal.state().button()).distinct().toList();
        var bigBlindRange = deals.stream().map(deal -> deal.state().bigBlind()).distinct().toList();
        var posterior =
                postflop.posteriorWeights(
                        preflop.posteriorWeights(
                                buttonRange, PreflopActionBelief.ObservedAction.BUTTON_OPEN),
                        state,
                        false);
        var response =
                new StrategicRiverCallPolicy(
                        bigBlindRange, preflop, postflop, 0.25, 0.75, game.potBb() / 2, 8);
        assertEquals(
                PhysicalPostflopActionBeliefAudit.exactBetIncrement(
                        state, deals, preflop, postflop, response, game.potBb() / 2, 8),
                PublicRiverResponseValueBucket.betIncrement(
                        board,
                        state.bigBlind(),
                        posterior,
                        game.potBb() / 2,
                        8,
                        response,
                        PublicRiverHistory.from(state)),
                1e-12);
    }

    @Test
    void rejectsMissingLegalOpponentAndInvalidPublicInputs() {
        var board = cards("2c", "7s", "Th", "3d", "9c");
        var bb = combo("Ac", "Kc");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PublicRiverResponseValueBucket.betIncrement(
                                board,
                                bb,
                                List.of(combo("2c", "Ah")),
                                3.25,
                                8,
                                PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PublicRiverResponseValueBucket.betIncrement(
                                board,
                                bb,
                                List.of(combo("Ad", "Ah")),
                                3.25,
                                0,
                                PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PublicRiverResponseValueBucket.betIncrement(
                                board,
                                combo("2c", "Kc"),
                                List.of(combo("Ad", "Ah")),
                                3.25,
                                8,
                                PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL));
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    private static List<Card> cards(String... values) {
        return new ArrayList<>(java.util.Arrays.stream(values).map(Card::parse).toList());
    }
}
