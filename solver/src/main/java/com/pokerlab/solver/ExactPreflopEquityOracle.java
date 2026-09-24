package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exhaustive heads-up preflop equity for one exact combo matchup; intended for payoff validation.
 */
public final class ExactPreflopEquityOracle implements PreflopEquityOracle {
    static final int BOARD_RUNOUTS = 1_712_304;
    private static final List<int[]> SUIT_PERMUTATIONS = suitPermutations();
    private final Map<Integer, EquityEstimate> cache = new ConcurrentHashMap<>();

    @Override
    public EquityEstimate estimate(WeightedCombo first, WeightedCombo second) {
        if (first.conflictsWith(second))
            throw new IllegalArgumentException("Opponent combos share a card");
        return cache.computeIfAbsent(
                canonicalMatchupKey(first, second), ignored -> enumerate(first, second));
    }

    int uniqueMatchupsEnumerated() {
        return cache.size();
    }

    /** Suit relabellings preserve exact equity, but the two player roles remain distinct. */
    static int canonicalMatchupKey(WeightedCombo first, WeightedCombo second) {
        int minimum = Integer.MAX_VALUE;
        for (int[] permutation : SUIT_PERMUTATIONS) {
            int firstA = cardCode(first.first(), permutation);
            int firstB = cardCode(first.second(), permutation);
            int secondA = cardCode(second.first(), permutation);
            int secondB = cardCode(second.second(), permutation);
            int key =
                    (Math.min(firstA, firstB) << 18)
                            | (Math.max(firstA, firstB) << 12)
                            | (Math.min(secondA, secondB) << 6)
                            | Math.max(secondA, secondB);
            minimum = Math.min(minimum, key);
        }
        return minimum;
    }

    private static int cardCode(Card card, int[] permutation) {
        return card.rank().ordinal() * 4 + permutation[card.suit().ordinal()];
    }

    private static List<int[]> suitPermutations() {
        List<int[]> permutations = new ArrayList<>(24);
        for (int first = 0; first < 4; first++)
            for (int second = 0; second < 4; second++)
                for (int third = 0; third < 4; third++)
                    for (int fourth = 0; fourth < 4; fourth++)
                        if (first != second
                                && first != third
                                && first != fourth
                                && second != third
                                && second != fourth
                                && third != fourth)
                            permutations.add(new int[] {first, second, third, fourth});
        return List.copyOf(permutations);
    }

    private static EquityEstimate enumerate(WeightedCombo first, WeightedCombo second) {
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
        if (boards != BOARD_RUNOUTS) throw new IllegalStateException("Unexpected board count");
        return new EquityEstimate((wins + 0.5 * ties) / boards, 0, boards);
    }
}
