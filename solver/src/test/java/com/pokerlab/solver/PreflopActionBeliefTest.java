package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PreflopActionBeliefTest {
    private static final List<WeightedCombo> BUTTON =
            List.of(combo("Ah", "Ad", 0.5), combo("Qh", "Jh", 1), combo("7c", "7d", 1));
    private static final List<WeightedCombo> BIG_BLIND =
            List.of(combo("Ac", "Kc", 1), combo("Tc", "Td", 1), combo("8h", "8s", 1));

    @Test
    void posteriorMarginMatchesIndependentlyWeightedJointDeals() {
        var belief = belief();
        var game = game(belief);
        List<Card> board = cards("2c", "3d", "4h", "5s", "9c");
        WeightedCombo own = BIG_BLIND.getFirst();
        double expected = 0;
        double total = 0;
        int ownScore = score(own, board);
        for (var deal : game.chanceOutcomes(game.initialState())) {
            var state = deal.state();
            if (!state.bigBlind().equals(own)) continue;
            WeightedCombo button = state.button();
            if (board.contains(button.first()) || board.contains(button.second())) continue;
            double weight =
                    deal.probability()
                            * belief.likelihood(
                                    PreflopActionBelief.ObservedAction.BUTTON_OPEN, button);
            total += weight;
            expected += weight * Integer.compare(ownScore, score(button, board));
        }
        double posteriorMargin =
                PublicRiverEquityBucket.showdownMargin(
                        board,
                        own,
                        belief.posteriorWeights(
                                BUTTON, PreflopActionBelief.ObservedAction.BUTTON_OPEN));
        assertEquals(expected / total, posteriorMargin, 1e-12);
        double priorMargin = PublicRiverEquityBucket.showdownMargin(board, own, BUTTON);
        assertNotEquals(priorMargin, posteriorMargin);
    }

    @Test
    void riverInformationSetUsesObservedActionAndNeverDealtOpponentCards() {
        var conditioned = game(belief());
        var staticRange =
                new ButtonBigBlindPhysicalDeckGame(
                        BUTTON,
                        BIG_BLIND,
                        2,
                        4,
                        8,
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS);
        List<Card> board = cards("2c", "3d", "4h", "5s", "9c");
        var first = state(BIG_BLIND.getFirst(), BUTTON.getFirst(), board);
        var second = state(BIG_BLIND.getFirst(), BUTTON.get(1), board);
        assertEquals(conditioned.informationSet(first), conditioned.informationSet(second));
        assertNotEquals(conditioned.contentHash(), staticRange.contentHash());
        assertEquals(
                conditioned.chanceOutcomes(conditioned.initialState()),
                staticRange.chanceOutcomes(staticRange.initialState()));
        assertEquals(
                conditioned.terminalUtility(
                        conditioned.afterAction(conditioned.afterAction(first, "k"), "k")),
                staticRange.terminalUtility(
                        staticRange.afterAction(staticRange.afterAction(first, "k"), "k")));
        assertTrue(conditioned.informationSet(first).contains("|R:"));
        var firstBlind = state(BIG_BLIND.getFirst(), BUTTON.getFirst(), board);
        var secondBlind = state(BIG_BLIND.get(1), BUTTON.getFirst(), board);
        firstBlind = conditioned.afterAction(firstBlind, "k");
        secondBlind = conditioned.afterAction(secondBlind, "k");
        assertEquals(
                conditioned.informationSet(firstBlind), conditioned.informationSet(secondBlind));
    }

    @Test
    void beliefIsValidatedAndIncludedInGameIdentity() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PreflopActionBelief(Map.of("Ah Ad", 0.0), Map.of("Ac Kc", 1.0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ButtonBigBlindPhysicalDeckGame(
                                BUTTON,
                                BIG_BLIND,
                                2,
                                4,
                                8,
                                ButtonBigBlindPhysicalDeckGame.InformationMode
                                        .PREFLOP_ACTION_RIVER_BUCKETS));
        var incomplete =
                new PreflopActionBelief(
                        Map.of("Ah Ad", 1.0), Map.of("Ac Kc", 1.0, "Tc Td", 1.0, "8h 8s", 1.0));
        assertThrows(IllegalArgumentException.class, () -> game(incomplete));
        var distinct =
                new PreflopActionBelief(
                        Map.of("Ad Ah", 0.8, "Jh Qh", 0.5, "7c 7d", 0.2),
                        Map.of("Ac Kc", 0.9, "Tc Td", 0.5, "8h 8s", 0.2));
        assertNotEquals(belief().contentDefinition(), distinct.contentDefinition());
        assertNotEquals(game(belief()).contentHash(), game(distinct).contentHash());
    }

    @Test
    void uniformActionLikelihoodLeavesTheStaticRiverObservationUnchanged() {
        var uniform =
                new PreflopActionBelief(
                        Map.of("Ad Ah", 0.5, "Jh Qh", 0.5, "7c 7d", 0.5),
                        Map.of("Ac Kc", 0.5, "Tc Td", 0.5, "8h 8s", 0.5));
        var conditioned = game(uniform);
        var staticRange =
                new ButtonBigBlindPhysicalDeckGame(
                        BUTTON,
                        BIG_BLIND,
                        2,
                        4,
                        8,
                        ButtonBigBlindPhysicalDeckGame.InformationMode.RANGE_EQUITY_RIVER_BUCKETS);
        var state =
                state(BIG_BLIND.getFirst(), BUTTON.getFirst(), cards("2c", "3d", "4h", "5s", "9c"));
        assertEquals(staticRange.informationSet(state), conditioned.informationSet(state));
    }

    private static ButtonBigBlindPhysicalDeckGame game(PreflopActionBelief belief) {
        return new ButtonBigBlindPhysicalDeckGame(
                BUTTON,
                BIG_BLIND,
                2,
                4,
                8,
                ButtonBigBlindPhysicalDeckGame.InformationMode.PREFLOP_ACTION_RIVER_BUCKETS,
                belief);
    }

    private static PreflopActionBelief belief() {
        return new PreflopActionBelief(
                Map.of("Ad Ah", 0.8, "Jh Qh", 0.5, "7c 7d", 0.2),
                Map.of("Ac Kc", 0.8, "Tc Td", 0.5, "8h 8s", 0.2));
    }

    private static ButtonBigBlindPhysicalDeckGame.State state(
            WeightedCombo bb, WeightedCombo button, List<Card> board) {
        return new ButtonBigBlindPhysicalDeckGame.State(
                bb,
                button,
                "oc",
                new ArrayList<>(board.subList(0, 3)),
                "kk",
                board.get(3),
                "kk",
                board.get(4),
                "");
    }

    private static int score(WeightedCombo combo, List<Card> board) {
        return HandEvaluator.evaluateBestScore(
                combo.first(),
                combo.second(),
                board.get(0),
                board.get(1),
                board.get(2),
                board.get(3),
                board.get(4));
    }

    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static List<Card> cards(String... cards) {
        return java.util.Arrays.stream(cards).map(Card::parse).toList();
    }
}
