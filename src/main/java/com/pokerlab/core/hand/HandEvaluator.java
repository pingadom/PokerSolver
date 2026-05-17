package com.pokerlab.core.hand;

import com.pokerlab.core.card.Card;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class HandEvaluator {
    private static final int FIVE_CARD_HAND_SIZE = 5;

    private HandEvaluator() {
    }

    public static EvaluatedHand evaluateFive(List<Card> cards) {
        validateCardCount(cards, 5, 5);
        validateNoDuplicates(cards);

        boolean flush = isFlush(cards);
        Integer straightHigh = straightHighCard(cards);
        boolean straight = straightHigh != null;

        Map<Integer, Long> countsByRank = cards.stream()
                .collect(Collectors.groupingBy(card -> card.rank().value(), Collectors.counting()));

        List<Integer> ranksDescending = cards.stream()
                .map(card -> card.rank().value())
                .sorted(Comparator.reverseOrder())
                .toList();

        List<Map.Entry<Integer, Long>> groups = countsByRank.entrySet().stream()
                .sorted(Comparator
                        .<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry.<Integer, Long>comparingByKey().reversed()))
                .toList();

        if (straight && flush) {
            if (straightHigh == 14) {
                return evaluated(HandCategory.ROYAL_FLUSH, List.of(14), cards);
            }
            return evaluated(HandCategory.STRAIGHT_FLUSH, List.of(straightHigh), cards);
        }

        if (groups.get(0).getValue() == 4) {
            int quadRank = groups.get(0).getKey();
            int kicker = groups.stream()
                    .filter(entry -> entry.getValue() == 1)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
            return evaluated(HandCategory.FOUR_OF_A_KIND, List.of(quadRank, kicker), cards);
        }

        if (groups.get(0).getValue() == 3 && groups.get(1).getValue() == 2) {
            int tripRank = groups.get(0).getKey();
            int pairRank = groups.get(1).getKey();
            return evaluated(HandCategory.FULL_HOUSE, List.of(tripRank, pairRank), cards);
        }

        if (flush) {
            return evaluated(HandCategory.FLUSH, ranksDescending, cards);
        }

        if (straight) {
            return evaluated(HandCategory.STRAIGHT, List.of(straightHigh), cards);
        }

        if (groups.get(0).getValue() == 3) {
            int tripRank = groups.get(0).getKey();
            List<Integer> kickers = groups.stream()
                    .filter(entry -> entry.getValue() == 1)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            return evaluated(HandCategory.THREE_OF_A_KIND, join(tripRank, kickers), cards);
        }

        List<Integer> pairRanks = groups.stream()
                .filter(entry -> entry.getValue() == 2)
                .map(Map.Entry::getKey)
                .sorted(Comparator.reverseOrder())
                .toList();

        if (pairRanks.size() == 2) {
            int kicker = groups.stream()
                    .filter(entry -> entry.getValue() == 1)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
            return evaluated(HandCategory.TWO_PAIR, List.of(pairRanks.get(0), pairRanks.get(1), kicker), cards);
        }

        if (pairRanks.size() == 1) {
            int pairRank = pairRanks.get(0);
            List<Integer> kickers = groups.stream()
                    .filter(entry -> entry.getValue() == 1)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.reverseOrder())
                    .toList();
            return evaluated(HandCategory.ONE_PAIR, join(pairRank, kickers), cards);
        }

        return evaluated(HandCategory.HIGH_CARD, ranksDescending, cards);
    }

    /**
     * Evaluates the best 5-card hand from 5, 6, or 7 cards.
     * For Hold'em with 2 hole cards and 5 board cards, this checks all 21 possible 5-card combinations.
     */
    public static EvaluatedHand evaluateBest(List<Card> cards) {
        validateCardCount(cards, 5, 7);
        validateNoDuplicates(cards);

        EvaluatedHand best = null;
        int n = cards.size();

        for (int a = 0; a < n - 4; a++) {
            for (int b = a + 1; b < n - 3; b++) {
                for (int c = b + 1; c < n - 2; c++) {
                    for (int d = c + 1; d < n - 1; d++) {
                        for (int e = d + 1; e < n; e++) {
                            List<Card> candidate = List.of(
                                    cards.get(a), cards.get(b), cards.get(c), cards.get(d), cards.get(e)
                            );
                            EvaluatedHand evaluated = evaluateFive(candidate);
                            if (best == null || evaluated.compareTo(best) > 0) {
                                best = evaluated;
                            }
                        }
                    }
                }
            }
        }

        return best;
    }

    private static EvaluatedHand evaluated(HandCategory category, List<Integer> tiebreakers, List<Card> cards) {
        return new EvaluatedHand(new HandRank(category, tiebreakers), cards);
    }

    private static boolean isFlush(List<Card> cards) {
        return cards.stream().map(Card::suit).distinct().count() == 1;
    }

    private static Integer straightHighCard(List<Card> cards) {
        Set<Integer> uniqueRanks = new HashSet<>();
        for (Card card : cards) {
            uniqueRanks.add(card.rank().value());
        }

        if (uniqueRanks.size() != 5) {
            return null;
        }

        List<Integer> sorted = uniqueRanks.stream()
                .sorted(Comparator.reverseOrder())
                .toList();

        // Wheel straight: A-2-3-4-5. Ace is counted as low, so the high card is 5.
        if (uniqueRanks.containsAll(List.of(14, 5, 4, 3, 2))) {
            return 5;
        }

        int high = sorted.get(0);
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i) != high - i) {
                return null;
            }
        }
        return high;
    }

    private static List<Integer> join(int first, List<Integer> rest) {
        List<Integer> result = new ArrayList<>();
        result.add(first);
        result.addAll(rest);
        return result;
    }

    private static void validateCardCount(List<Card> cards, int min, int max) {
        if (cards == null) {
            throw new IllegalArgumentException("cards must not be null");
        }
        if (cards.size() < min || cards.size() > max) {
            throw new IllegalArgumentException("Expected between " + min + " and " + max + " cards, got " + cards.size());
        }
    }

    private static void validateNoDuplicates(List<Card> cards) {
        Set<Card> unique = new HashSet<>(cards);
        if (unique.size() != cards.size()) {
            Map<Card, Integer> counts = new HashMap<>();
            for (Card card : cards) {
                counts.merge(card, 1, Integer::sum);
            }
            throw new IllegalArgumentException("Duplicate cards are not allowed: " + counts);
        }
    }
}
