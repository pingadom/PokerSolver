package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class ButtonBigBlindContinuationGameTest {
    private static Card card(String text) {
        return Card.parse(text);
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(card(first), card(second), 1);
    }

    private static final List<WeightedCombo> BUTTON =
            List.of(new WeightedCombo(card("Ac"), card("Ad"), 0.25), combo("Kh", "Qh"));
    private static final List<WeightedCombo> BIG_BLIND =
            List.of(combo("Jc", "Jd"), combo("As", "Ks"));
    private static final List<Card> FLOP_ONE = List.of(card("2c"), card("7d"), card("Th"));
    private static final List<Card> FLOP_TWO = List.of(card("Ac"), card("7c"), card("2d"));

    private static ButtonBigBlindContinuationGame game(List<List<Card>> flops) {
        return new ButtonBigBlindContinuationGame(
                BUTTON, BIG_BLIND, flops, List.of(card("3s"), card("4s")), 2, 4, 8);
    }

    private static ButtonBigBlindContinuationGame.State deal(
            ButtonBigBlindContinuationGame game, String bb, String btn) {
        return game.chanceOutcomes(game.initialState()).stream()
                .map(ChanceOutcome::state)
                .filter(state -> state.bigBlind().key().equals(bb))
                .filter(state -> state.button().key().equals(btn))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void preflopFoldPayoffsAndPostflopStartShareOneChipAccountingModel() {
        var game = game(List.of(FLOP_ONE));
        assertThrows(IllegalArgumentException.class, () -> game.legalActions(game.initialState()));
        var start = deal(game, "Jc Jd", "Ac Ad");
        assertEquals(1, game.currentPlayer(start));
        assertEquals(List.of("open3", "fold"), game.legalActions(start));
        assertEquals(0.25, game.terminalUtility(game.afterAction(start, "fold")));

        var opened = game.afterAction(start, "open3");
        assertEquals(0, game.currentPlayer(opened));
        assertEquals(List.of("call", "fold"), game.legalActions(opened));
        assertEquals(-1.25, game.terminalUtility(game.afterAction(opened, "fold")));
        var called = game.afterAction(opened, "call");
        assertEquals(-1, game.currentPlayer(called));
        assertThrows(IllegalArgumentException.class, () -> game.legalActions(called));
        var flop = game.chanceOutcomes(called).get(0).state();
        assertEquals(0, game.currentPlayer(flop));
        assertEquals(6.5, game.flopSpots().get(0).potBb());
        assertEquals(97, game.flopSpots().get(0).remainingStackBb());
        assertTrue(game.informationSet(flop).contains("2c7dTh"));
        assertTrue(game.informationSet(flop).contains("Jc Jd"));
        assertFalse(game.informationSet(flop).contains("Ac Ad"));
        var flopBet = game.afterAction(flop, "b");
        assertEquals(1, game.currentPlayer(flopBet));
        var flopFold = game.afterAction(flopBet, "f");
        assertEquals(3.25, game.terminalUtility(flopFold));
        assertThrows(IllegalArgumentException.class, () -> game.afterAction(start, "call"));
    }

    @Test
    void flopChanceRemovesPrivateCardBlockersAndNormalizesForEachDeal() {
        var game = game(List.of(FLOP_ONE, FLOP_TWO));
        var blocked =
                game.afterAction(game.afterAction(deal(game, "Jc Jd", "Ac Ad"), "open3"), "call");
        var unblocked =
                game.afterAction(game.afterAction(deal(game, "Jc Jd", "Kh Qh"), "open3"), "call");
        assertEquals(1, game.chanceOutcomes(blocked).size());
        assertEquals(2, game.chanceOutcomes(unblocked).size());
        assertEquals(0.5, game.chanceOutcomes(unblocked).get(0).probability());
        assertEquals(
                1,
                game.chanceOutcomes(unblocked).stream()
                        .mapToDouble(ChanceOutcome::probability)
                        .sum(),
                1e-12);
        assertNotEquals(
                game.informationSet(game.chanceOutcomes(unblocked).get(0).state()),
                game.informationSet(game.chanceOutcomes(unblocked).get(1).state()));
    }

    @Test
    void aCalledPotKeepsTheUncontestedBlindAsDeadMoney() {
        var game = game(List.of(FLOP_ONE));
        double againstAces = checkdown(game, deal(game, "Jc Jd", "Ac Ad"));
        double againstBroadway = checkdown(game, deal(game, "Jc Jd", "Kh Qh"));
        assertTrue(againstAces < 0);
        assertTrue(againstBroadway > 0);
        assertTrue((againstAces + againstBroadway) / 2 > -1.25);
    }

    private static double checkdown(
            ButtonBigBlindContinuationGame game, ButtonBigBlindContinuationGame.State deal) {
        var called = game.afterAction(game.afterAction(deal, "open3"), "call");
        double total = 0;
        for (var flop : game.chanceOutcomes(called)) {
            var flopChecked = game.afterAction(game.afterAction(flop.state(), "k"), "k");
            for (var turn : game.chanceOutcomes(flopChecked)) {
                var turnChecked = game.afterAction(game.afterAction(turn.state(), "k"), "k");
                for (var river : game.chanceOutcomes(turnChecked)) {
                    var finalState = game.afterAction(game.afterAction(river.state(), "k"), "k");
                    total +=
                            flop.probability()
                                    * turn.probability()
                                    * river.probability()
                                    * game.terminalUtility(finalState);
                }
            }
        }
        return total;
    }

    @Test
    void solvesPreflopAndAllThreeStreetsInTheSameInformationSetTree() {
        var game = game(List.of(FLOP_ONE));
        var solver = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS);
        var early = HeadsUpBestResponse.assess(game, solver.solve(5));
        var solution = solver.solve(100);
        var later = HeadsUpBestResponse.assess(game, solution);
        assertTrue(solution.strategy().containsKey("1:P:BTN:Ac Ad"));
        assertTrue(solution.strategy().containsKey("0:P:BB:open3:Jc Jd"));
        assertTrue(solution.strategy().keySet().stream().anyMatch(key -> key.contains("|T:")));
        assertTrue(solution.strategy().keySet().stream().anyMatch(key -> key.contains("|R:")));
        assertTrue(later.gap() < early.gap());
        assertTrue(later.gap() < 0.1, "This tiny game should approach its own equilibrium");
        assertTrue(solution.strategy().get("1:P:BTN:Kh Qh").get("open3") > 0.2);
        assertTrue(solution.strategy().get("1:P:BTN:Kh Qh").get("open3") < 0.8);
        assertTrue(solution.strategy().get("0:P:BB:open3:Jc Jd").get("call") > 0.1);
        assertTrue(solution.strategy().get("0:P:BB:open3:Jc Jd").get("call") < 0.9);
    }

    @Test
    void rejectsDuplicateOrImpossibleChanceCandidates() {
        assertThrows(
                IllegalArgumentException.class, () -> game(List.of(FLOP_ONE, FLOP_ONE.reversed())));
        assertThrows(
                IllegalArgumentException.class,
                () -> game(List.of(List.of(card("Ac"), card("Kh"), card("2d")))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ButtonBigBlindContinuationGame(
                                BUTTON,
                                BIG_BLIND,
                                List.of(FLOP_ONE),
                                List.of(card("2c")),
                                2,
                                4,
                                8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ButtonBigBlindContinuationGame(
                                BUTTON,
                                BIG_BLIND,
                                List.of(FLOP_ONE),
                                new com.pokerlab.core.card.Deck().cards(),
                                2,
                                4,
                                8));
    }

    @Test
    void gameHashCapturesInputsButIgnoresTheirPresentationOrder() {
        var original = game(List.of(FLOP_ONE, FLOP_TWO));
        var reordered =
                new ButtonBigBlindContinuationGame(
                        BUTTON.reversed(),
                        BIG_BLIND.reversed(),
                        List.of(FLOP_TWO.reversed(), FLOP_ONE.reversed()),
                        List.of(card("4s"), card("3s")),
                        2,
                        4,
                        8);
        assertEquals(original.contentHash(), reordered.contentHash());
        var changed =
                new ButtonBigBlindContinuationGame(
                        List.of(combo("Ac", "Ad"), combo("Kh", "Qh")),
                        BIG_BLIND,
                        List.of(FLOP_ONE, FLOP_TWO),
                        List.of(card("3s"), card("4s")),
                        2,
                        4,
                        8);
        assertNotEquals(original.contentHash(), changed.contentHash());
    }
}
