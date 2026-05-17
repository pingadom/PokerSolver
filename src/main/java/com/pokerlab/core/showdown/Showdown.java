package com.pokerlab.core.showdown;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.EvaluatedHand;
import com.pokerlab.core.hand.HandEvaluator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class Showdown {

    private Showdown() {
    }

    public static ShowdownResult compare(List<Card> heroHoleCards, List<Card> villainHoleCards, List<Card> boardCards) {
        validateSize("heroHoleCards", heroHoleCards, 2);
        validateSize("villainHoleCards", villainHoleCards, 2);
        if (boardCards == null || boardCards.size() < 3 || boardCards.size() > 5) {
            throw new IllegalArgumentException("boardCards must contain between 3 and 5 cards");
        }

        List<Card> allCards = new ArrayList<>();
        allCards.addAll(heroHoleCards);
        allCards.addAll(villainHoleCards);
        allCards.addAll(boardCards);
        validateNoDuplicates(allCards);

        List<Card> heroCards = new ArrayList<>();
        heroCards.addAll(heroHoleCards);
        heroCards.addAll(boardCards);

        List<Card> villainCards = new ArrayList<>();
        villainCards.addAll(villainHoleCards);
        villainCards.addAll(boardCards);

        EvaluatedHand heroBest = HandEvaluator.evaluateBest(heroCards);
        EvaluatedHand villainBest = HandEvaluator.evaluateBest(villainCards);

        int comparison = heroBest.compareTo(villainBest);
        Winner winner = comparison > 0 ? Winner.HERO : comparison < 0 ? Winner.VILLAIN : Winner.TIE;

        return new ShowdownResult(heroBest, villainBest, winner);
    }

    private static void validateSize(String name, List<Card> cards, int expectedSize) {
        if (cards == null || cards.size() != expectedSize) {
            throw new IllegalArgumentException(name + " must contain exactly " + expectedSize + " cards");
        }
    }

    private static void validateNoDuplicates(List<Card> cards) {
        Set<Card> unique = new HashSet<>(cards);
        if (unique.size() != cards.size()) {
            throw new IllegalArgumentException("Duplicate cards are not allowed in a showdown");
        }
    }
}
