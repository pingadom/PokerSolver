package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.List;

/**
 * Exhaustive heads-up preflop equity for one exact combo matchup; intended for payoff validation.
 */
public final class ExactPreflopEquityOracle implements PreflopEquityOracle {
    @Override
    public EquityEstimate estimate(WeightedCombo first, WeightedCombo second) {
        if (first.conflictsWith(second))
            throw new IllegalArgumentException("Opponent combos share a card");
        List<Card> available =
                new Deck()
                        .cards().stream()
                                .filter(
                                        card ->
                                                !card.equals(first.first())
                                                        && !card.equals(first.second())
                                                        && !card.equals(second.first())
                                                        && !card.equals(second.second()))
                                .toList();
        Card[] deck = available.toArray(Card[]::new);
        int wins = 0;
        int ties = 0;
        int boards = 0;
        for (int a = 0; a < deck.length - 4; a++) {
            for (int b = a + 1; b < deck.length - 3; b++) {
                for (int c = b + 1; c < deck.length - 2; c++) {
                    for (int d = c + 1; d < deck.length - 1; d++) {
                        for (int e = d + 1; e < deck.length; e++) {
                            int firstScore =
                                    HandEvaluator.evaluateBestScore(
                                            first.first(),
                                            first.second(),
                                            deck[a],
                                            deck[b],
                                            deck[c],
                                            deck[d],
                                            deck[e]);
                            int secondScore =
                                    HandEvaluator.evaluateBestScore(
                                            second.first(),
                                            second.second(),
                                            deck[a],
                                            deck[b],
                                            deck[c],
                                            deck[d],
                                            deck[e]);
                            if (firstScore > secondScore) wins++;
                            else if (firstScore == secondScore) ties++;
                            boards++;
                        }
                    }
                }
            }
        }
        return new EquityEstimate((wins + 0.5 * ties) / boards, 0, boards);
    }
}
