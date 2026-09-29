package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.List;

/**
 * Exact BB river bet increment when BTN calls with at least one pair and folds high-card hands.
 * This fixed hand-dependent response is an independent abstraction probe, not rational play.
 */
public final class PhysicalRiverPairCallResponse {
    private PhysicalRiverPairCallResponse() {}

    public static double betIncrement(
            ButtonBigBlindPhysicalDeckGame.State state,
            List<ChanceOutcome<ButtonBigBlindPhysicalDeckGame.State>> deals,
            double halfPotBb,
            double riverBetBb) {
        if (state.river() == null || !state.riverHistory().isEmpty())
            throw new IllegalArgumentException("Expected first BB river decision");
        List<Card> board = new ArrayList<>(state.flop());
        board.add(state.turn());
        board.add(state.river());
        int bbScore = score(state.bigBlind(), board);
        double legalWeight = 0;
        double weightedIncrement = 0;
        for (var outcome : deals) {
            var candidate = outcome.state();
            if (!candidate.bigBlind().equals(state.bigBlind())) continue;
            WeightedCombo button = candidate.button();
            if (board.contains(button.first()) || board.contains(button.second())) continue;
            int btnScore = score(button, board);
            int sign = Integer.compare(bbScore, btnScore);
            double increment = calls(button, board) ? riverBetBb * sign : halfPotBb * (1 - sign);
            legalWeight += outcome.probability();
            weightedIncrement += outcome.probability() * increment;
        }
        if (legalWeight == 0) throw new IllegalStateException("No legal opponent on sampled board");
        return weightedIncrement / legalWeight;
    }

    static boolean calls(WeightedCombo hand, List<Card> board) {
        return HandEvaluator.evaluateBest(
                                hand.first(),
                                hand.second(),
                                board.get(0),
                                board.get(1),
                                board.get(2),
                                board.get(3),
                                board.get(4))
                        .rank()
                        .category()
                        .strength()
                >= 1;
    }

    private static int score(WeightedCombo hand, List<Card> board) {
        return HandEvaluator.evaluateBestScore(
                hand.first(),
                hand.second(),
                board.get(0),
                board.get(1),
                board.get(2),
                board.get(3),
                board.get(4));
    }
}
