package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class PublicRiverEquityBucketTest {
    @Test
    void marginMatchesIndependentDealWeightedShowdownAudit() {
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS,
                        ButtonBigBlindRangeValidationFixture.RangeProfile.STRESS_5X5);
        var deals = game.chanceOutcomes(game.initialState());
        var random = new SplittableRandom(42);
        for (int attempt = 0; attempt < 200; attempt++) {
            var state = PhysicalRiverAliasAudit.sampleRiverState(game, random);
            List<WeightedCombo> opponents =
                    deals.stream()
                            .map(ChanceOutcome::state)
                            .filter(deal -> deal.bigBlind().equals(state.bigBlind()))
                            .map(ButtonBigBlindPhysicalDeckGame.State::button)
                            .toList();
            assertEquals(
                    PhysicalRiverAliasAudit.calledBetMargin(state, deals),
                    PublicRiverEquityBucket.showdownMargin(
                            board(state), state.bigBlind(), opponents),
                    1e-12);
        }
    }

    @Test
    void informationSetUsesOnlyOwnCardsPublicBoardAndDeclaredPrior() {
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS,
                        ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3);
        var deals = game.chanceOutcomes(game.initialState());
        var state = PhysicalRiverAliasAudit.sampleRiverState(game, new SplittableRandom(43));
        var alternate =
                deals.stream()
                        .map(ChanceOutcome::state)
                        .filter(deal -> deal.bigBlind().equals(state.bigBlind()))
                        .map(ButtonBigBlindPhysicalDeckGame.State::button)
                        .filter(hand -> !hand.equals(state.button()))
                        .filter(hand -> !board(state).contains(hand.first()))
                        .filter(hand -> !board(state).contains(hand.second()))
                        .findFirst()
                        .orElseThrow();
        var sameObservation =
                new ButtonBigBlindPhysicalDeckGame.State(
                        state.bigBlind(),
                        alternate,
                        state.preflopHistory(),
                        state.flop(),
                        state.flopHistory(),
                        state.turn(),
                        state.turnHistory(),
                        state.river(),
                        state.riverHistory());
        assertEquals(game.informationSet(state), game.informationSet(sameObservation));
        assertTrue(game.informationSet(state).contains("|R:m"));
        assertNotEquals(
                game.contentHash(),
                ButtonBigBlindRangeValidationFixture.createCoarseBucketed().contentHash());
        assertEquals(
                game.terminalUtility(game.afterAction(game.afterAction(state, "b"), "c")),
                ButtonBigBlindRangeValidationFixture.createCoarseBucketed()
                        .terminalUtility(game.afterAction(game.afterAction(state, "b"), "c")));
    }

    @Test
    void rejectsIllegalRiverInputsAndFullyBlockedPriors() {
        var own = new WeightedCombo(Card.parse("Ac"), Card.parse("Kc"), 1);
        var opponent = new WeightedCombo(Card.parse("Qh"), Card.parse("Jh"), 1);
        var board =
                List.of(
                        Card.parse("2c"),
                        Card.parse("3d"),
                        Card.parse("4s"),
                        Card.parse("5h"),
                        Card.parse("6c"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PublicRiverEquityBucket.showdownMargin(
                                board.subList(0, 4), own, List.of(opponent)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PublicRiverEquityBucket.showdownMargin(board, own, List.of(own)));
        assertThrows(
                IllegalArgumentException.class,
                () -> PublicRiverEquityBucket.showdownMargin(board, own, List.of()));
    }

    private static List<Card> board(ButtonBigBlindPhysicalDeckGame.State state) {
        List<Card> board = new ArrayList<>(state.flop());
        board.add(state.turn());
        board.add(state.river());
        return board;
    }
}
