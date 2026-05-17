package com.pokerlab.core.hand;

import com.pokerlab.core.card.Card;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HandEvaluator {
    private static final int FIVE_CARD_HAND_SIZE = 5;
    private static final int MIN_BEST_HAND_SIZE = 5;
    private static final int MAX_BEST_HAND_SIZE = 7;
    private static final int MAX_RANK_VALUE = 14;
    private static final int CATEGORY_SHIFT = 20;
    private static final int FIRST_TIEBREAKER_SHIFT = 16;
    private static final int TIEBREAKER_BITS = 4;
    private static final int[] STRAIGHT_HIGH_BY_MASK = buildStraightLookup();
    private static final HandCategory[] CATEGORY_BY_STRENGTH = buildCategoryLookup();

    private HandEvaluator() {
    }

    public static EvaluatedHand evaluateFive(List<Card> cards) {
        validateCardCount(cards, FIVE_CARD_HAND_SIZE, FIVE_CARD_HAND_SIZE);
        validateNoDuplicates(cards);

        int score = scoreFive(
                cards.get(0),
                cards.get(1),
                cards.get(2),
                cards.get(3),
                cards.get(4)
        );

        return evaluated(score, cards);
    }

    /**
     * Evaluates the best 5-card hand from 5, 6, or 7 cards.
     * For Hold'em with 2 hole cards and 5 board cards, this checks all 21 possible 5-card combinations.
     */
    public static EvaluatedHand evaluateBest(List<Card> cards) {
        validateCardCount(cards, MIN_BEST_HAND_SIZE, MAX_BEST_HAND_SIZE);

        Card[] cardArray = cards.toArray(Card[]::new);
        validateNoDuplicates(cardArray);

        return evaluateBestArray(cardArray);
    }

    public static EvaluatedHand evaluateBest(Card... cards) {
        validateCardCount(cards, MIN_BEST_HAND_SIZE, MAX_BEST_HAND_SIZE);
        validateNoDuplicates(cards);

        return evaluateBestArray(cards);
    }

    public static int evaluateBestScore(Card... cards) {
        validateCardCount(cards, MIN_BEST_HAND_SIZE, MAX_BEST_HAND_SIZE);
        validateNoDuplicates(cards);

        return bestScore(cards);
    }

    private static int bestScore(Card[] cards) {
        int bestScore = -1;
        int n = cards.length;

        for (int a = 0; a < n - 4; a++) {
            Card cardA = cards[a];
            for (int b = a + 1; b < n - 3; b++) {
                Card cardB = cards[b];
                for (int c = b + 1; c < n - 2; c++) {
                    Card cardC = cards[c];
                    for (int d = c + 1; d < n - 1; d++) {
                        Card cardD = cards[d];
                        for (int e = d + 1; e < n; e++) {
                            int score = scoreFive(cardA, cardB, cardC, cardD, cards[e]);
                            if (score > bestScore) {
                                bestScore = score;
                            }
                        }
                    }
                }
            }
        }

        return bestScore;
    }

    private static EvaluatedHand evaluateBestArray(Card[] cards) {
        int bestScore = -1;
        int bestA = 0;
        int bestB = 1;
        int bestC = 2;
        int bestD = 3;
        int bestE = 4;
        int n = cards.length;

        for (int a = 0; a < n - 4; a++) {
            Card cardA = cards[a];
            for (int b = a + 1; b < n - 3; b++) {
                Card cardB = cards[b];
                for (int c = b + 1; c < n - 2; c++) {
                    Card cardC = cards[c];
                    for (int d = c + 1; d < n - 1; d++) {
                        Card cardD = cards[d];
                        for (int e = d + 1; e < n; e++) {
                            int score = scoreFive(cardA, cardB, cardC, cardD, cards[e]);
                            if (score > bestScore) {
                                bestScore = score;
                                bestA = a;
                                bestB = b;
                                bestC = c;
                                bestD = d;
                                bestE = e;
                            }
                        }
                    }
                }
            }
        }

        return evaluated(bestScore, List.of(cards[bestA], cards[bestB], cards[bestC], cards[bestD], cards[bestE]));
    }

    private static int scoreFive(Card c0, Card c1, Card c2, Card c3, Card c4) {
        int r0 = c0.rank().value();
        int r1 = c1.rank().value();
        int r2 = c2.rank().value();
        int r3 = c3.rank().value();
        int r4 = c4.rank().value();

        boolean flush = c0.suit() == c1.suit()
                && c0.suit() == c2.suit()
                && c0.suit() == c3.suit()
                && c0.suit() == c4.suit();

        int straightHigh = STRAIGHT_HIGH_BY_MASK[
                (1 << r0) | (1 << r1) | (1 << r2) | (1 << r3) | (1 << r4)
        ];

        int swap;
        if (r0 < r1) {
            swap = r0;
            r0 = r1;
            r1 = swap;
        }
        if (r1 < r2) {
            swap = r1;
            r1 = r2;
            r2 = swap;
        }
        if (r2 < r3) {
            swap = r2;
            r2 = r3;
            r3 = swap;
        }
        if (r3 < r4) {
            swap = r3;
            r3 = r4;
            r4 = swap;
        }
        if (r0 < r1) {
            swap = r0;
            r0 = r1;
            r1 = swap;
        }
        if (r1 < r2) {
            swap = r1;
            r1 = r2;
            r2 = swap;
        }
        if (r2 < r3) {
            swap = r2;
            r2 = r3;
            r3 = swap;
        }
        if (r0 < r1) {
            swap = r0;
            r0 = r1;
            r1 = swap;
        }
        if (r1 < r2) {
            swap = r1;
            r1 = r2;
            r2 = swap;
        }
        if (r0 < r1) {
            swap = r0;
            r0 = r1;
            r1 = swap;
        }

        if (straightHigh != 0 && flush) {
            if (straightHigh == MAX_RANK_VALUE) {
                return pack(HandCategory.ROYAL_FLUSH, MAX_RANK_VALUE, 0, 0, 0, 0);
            }
            return pack(HandCategory.STRAIGHT_FLUSH, straightHigh, 0, 0, 0, 0);
        }

        if (r0 == r3) {
            return pack(HandCategory.FOUR_OF_A_KIND, r0, r4, 0, 0, 0);
        }
        if (r1 == r4) {
            return pack(HandCategory.FOUR_OF_A_KIND, r1, r0, 0, 0, 0);
        }

        if (r0 == r2 && r3 == r4) {
            return pack(HandCategory.FULL_HOUSE, r0, r3, 0, 0, 0);
        }
        if (r0 == r1 && r2 == r4) {
            return pack(HandCategory.FULL_HOUSE, r2, r0, 0, 0, 0);
        }

        if (flush) {
            return pack(HandCategory.FLUSH, r0, r1, r2, r3, r4);
        }

        if (straightHigh != 0) {
            return pack(HandCategory.STRAIGHT, straightHigh, 0, 0, 0, 0);
        }

        if (r0 == r2) {
            return pack(HandCategory.THREE_OF_A_KIND, r0, r3, r4, 0, 0);
        }
        if (r1 == r3) {
            return pack(HandCategory.THREE_OF_A_KIND, r1, r0, r4, 0, 0);
        }
        if (r2 == r4) {
            return pack(HandCategory.THREE_OF_A_KIND, r2, r0, r1, 0, 0);
        }

        if (r0 == r1 && r2 == r3) {
            return pack(HandCategory.TWO_PAIR, r0, r2, r4, 0, 0);
        }
        if (r0 == r1 && r3 == r4) {
            return pack(HandCategory.TWO_PAIR, r0, r3, r2, 0, 0);
        }
        if (r1 == r2 && r3 == r4) {
            return pack(HandCategory.TWO_PAIR, r1, r3, r0, 0, 0);
        }

        if (r0 == r1) {
            return pack(HandCategory.ONE_PAIR, r0, r2, r3, r4, 0);
        }
        if (r1 == r2) {
            return pack(HandCategory.ONE_PAIR, r1, r0, r3, r4, 0);
        }
        if (r2 == r3) {
            return pack(HandCategory.ONE_PAIR, r2, r0, r1, r4, 0);
        }
        if (r3 == r4) {
            return pack(HandCategory.ONE_PAIR, r3, r0, r1, r2, 0);
        }

        return pack(HandCategory.HIGH_CARD, r0, r1, r2, r3, r4);
    }

    private static EvaluatedHand evaluated(int score, List<Card> cards) {
        return new EvaluatedHand(new HandRank(category(score), tiebreakers(score)), cards);
    }

    private static HandCategory category(int score) {
        return CATEGORY_BY_STRENGTH[score >>> CATEGORY_SHIFT];
    }

    private static List<Integer> tiebreakers(int score) {
        return switch (category(score)) {
            case HIGH_CARD, FLUSH -> List.of(
                    tiebreaker(score, 0),
                    tiebreaker(score, 1),
                    tiebreaker(score, 2),
                    tiebreaker(score, 3),
                    tiebreaker(score, 4)
            );
            case ONE_PAIR -> List.of(
                    tiebreaker(score, 0),
                    tiebreaker(score, 1),
                    tiebreaker(score, 2),
                    tiebreaker(score, 3)
            );
            case TWO_PAIR -> List.of(
                    tiebreaker(score, 0),
                    tiebreaker(score, 1),
                    tiebreaker(score, 2)
            );
            case THREE_OF_A_KIND, FULL_HOUSE, FOUR_OF_A_KIND -> List.of(
                    tiebreaker(score, 0),
                    tiebreaker(score, 1)
            );
            case STRAIGHT, STRAIGHT_FLUSH, ROYAL_FLUSH -> List.of(tiebreaker(score, 0));
        };
    }

    private static int tiebreaker(int score, int index) {
        return (score >>> (FIRST_TIEBREAKER_SHIFT - (index * TIEBREAKER_BITS))) & 0xF;
    }

    private static int pack(HandCategory category, int first, int second, int third, int fourth, int fifth) {
        return (category.strength() << CATEGORY_SHIFT)
                | (first << 16)
                | (second << 12)
                | (third << 8)
                | (fourth << 4)
                | fifth;
    }

    private static int[] buildStraightLookup() {
        int[] lookup = new int[1 << (MAX_RANK_VALUE + 1)];

        for (int high = 6; high <= MAX_RANK_VALUE; high++) {
            int mask = 0;
            for (int rank = high - 4; rank <= high; rank++) {
                mask |= 1 << rank;
            }
            lookup[mask] = high;
        }

        lookup[(1 << MAX_RANK_VALUE) | (1 << 5) | (1 << 4) | (1 << 3) | (1 << 2)] = 5;
        return lookup;
    }

    private static HandCategory[] buildCategoryLookup() {
        HandCategory[] categories = new HandCategory[HandCategory.values().length];
        for (HandCategory category : HandCategory.values()) {
            categories[category.strength()] = category;
        }
        return categories;
    }

    private static void validateCardCount(List<Card> cards, int min, int max) {
        if (cards == null) {
            throw new IllegalArgumentException("cards must not be null");
        }
        if (cards.size() < min || cards.size() > max) {
            throw new IllegalArgumentException("Expected between " + min + " and " + max + " cards, got " + cards.size());
        }
    }

    private static void validateCardCount(Card[] cards, int min, int max) {
        if (cards == null) {
            throw new IllegalArgumentException("cards must not be null");
        }
        if (cards.length < min || cards.length > max) {
            throw new IllegalArgumentException("Expected between " + min + " and " + max + " cards, got " + cards.length);
        }
    }

    private static void validateNoDuplicates(List<Card> cards) {
        long seen = 0L;

        for (Card card : cards) {
            long bit = 1L << cardIndex(card);
            if ((seen & bit) != 0L) {
                throwDuplicateCards(cards);
            }
            seen |= bit;
        }
    }

    private static void validateNoDuplicates(Card[] cards) {
        long seen = 0L;

        for (Card card : cards) {
            long bit = 1L << cardIndex(card);
            if ((seen & bit) != 0L) {
                throwDuplicateCards(cards);
            }
            seen |= bit;
        }
    }

    private static int cardIndex(Card card) {
        return (card.suit().ordinal() * 13) + card.rank().ordinal();
    }

    private static void throwDuplicateCards(List<Card> cards) {
        Map<Card, Integer> counts = new HashMap<>();
        for (Card card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        throw new IllegalArgumentException("Duplicate cards are not allowed: " + counts);
    }

    private static void throwDuplicateCards(Card[] cards) {
        Map<Card, Integer> counts = new HashMap<>();
        for (Card card : cards) {
            counts.merge(card, 1, Integer::sum);
        }
        throw new IllegalArgumentException("Duplicate cards are not allowed: " + counts);
    }
}
