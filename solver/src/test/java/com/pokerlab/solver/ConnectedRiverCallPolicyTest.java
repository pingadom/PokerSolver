package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectedRiverCallPolicyTest {
    @Test
    void readsMixedCallProbabilityAndCountsMissingInformationSets() {
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3);
        var deals = game.chanceOutcomes(game.initialState());
        var button = deals.stream().map(deal -> deal.state().button()).distinct().toList().get(1);
        var bigBlind =
                deals.stream().map(deal -> deal.state().bigBlind()).distinct().toList().getFirst();
        var board = cards("2c", "7s", "Th", "3d", "9c");
        var publicRiver = publicRiver(bigBlind, button, board);
        var facingBet = state(bigBlind, button, board, "b");
        String key = game.informationSet(facingBet);
        var anotherBigBlind =
                deals.stream()
                        .map(deal -> deal.state().bigBlind())
                        .distinct()
                        .filter(combo -> !combo.equals(bigBlind))
                        .findFirst()
                        .orElseThrow();
        assertEquals(key, game.informationSet(state(anotherBigBlind, button, board, "b")));
        var solution = new CfrSolution(1, Map.of("1:" + key, Map.of("c", 0.25, "f", 0.75)));
        var policy =
                new ConnectedRiverCallPolicy(
                        game,
                        solution,
                        RiverCallPolicy.fixed(PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL));
        assertEquals(0.25, policy.callProbability(button, board, publicRiver), 1e-12);
        assertEquals(0.25, policy.callProbability(button, board, publicRiver), 1e-12);
        var differentBoard = cards("2s", "5s", "8s", "Js", "Ks");
        assertEquals(
                1,
                policy.callProbability(
                        button, differentBoard, publicRiver(bigBlind, button, differentBoard)),
                1e-12);
        var coverage = policy.coverage();
        assertEquals(3, coverage.queries());
        assertEquals(2, coverage.learnedQueries());
        assertEquals(2, coverage.queriedInformationSets());
        assertEquals(1, coverage.learnedInformationSets());
        assertEquals(2.0 / 3, coverage.querySupportRate(), 1e-12);
        assertEquals(0.5, coverage.informationSetSupportRate(), 1e-12);
        assertTrue(
                policy.learnedCallProbability(
                                button,
                                differentBoard,
                                publicRiver(bigBlind, button, differentBoard))
                        .isEmpty());
    }

    @Test
    void rejectsInvalidLearnedActionProbabilities() {
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3);
        var dealt = game.chanceOutcomes(game.initialState()).getFirst().state();
        var board = cards("2c", "7s", "Th", "3d", "9c");
        String key = game.informationSet(state(dealt.bigBlind(), dealt.button(), board, "b"));
        var policy =
                new ConnectedRiverCallPolicy(
                        game,
                        new CfrSolution(1, Map.of("1:" + key, Map.of("c", 0.8, "f", 0.8))),
                        RiverCallPolicy.fixed(PhysicalActionBeliefAudit.ResponseModel.ALWAYS_CALL));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        policy.callProbability(
                                dealt.button(),
                                board,
                                publicRiver(dealt.bigBlind(), dealt.button(), board)));
    }

    private static List<Card> cards(String... labels) {
        return java.util.Arrays.stream(labels).map(Card::parse).toList();
    }

    private static PublicRiverHistory publicRiver(
            WeightedCombo bigBlind, WeightedCombo button, List<Card> board) {
        return PublicRiverHistory.from(state(bigBlind, button, board, ""));
    }

    private static ButtonBigBlindPhysicalDeckGame.State state(
            WeightedCombo bigBlind, WeightedCombo button, List<Card> board, String riverHistory) {
        return new ButtonBigBlindPhysicalDeckGame.State(
                bigBlind,
                button,
                "oc",
                board.subList(0, 3),
                "kk",
                board.get(3),
                "kk",
                board.get(4),
                riverHistory);
    }
}
