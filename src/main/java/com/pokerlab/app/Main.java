package com.pokerlab.app;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.showdown.Showdown;
import com.pokerlab.core.showdown.ShowdownResult;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Card> hero;
        List<Card> villain;
        List<Card> board;

        if (args.length == 3) {
            hero = parseCards(args[0]);
            villain = parseCards(args[1]);
            board = parseCards(args[2]);
        } else {
            hero = parseCards("AsKs");
            villain = parseCards("QdQc");
            board = parseCards("Ah7c2s9dJc");
            System.out.println("No arguments provided. Running sample showdown.\n");
        }

        ShowdownResult result = Showdown.compare(hero, villain, board);

        System.out.println("Hero:    " + hero);
        System.out.println("Villain: " + villain);
        System.out.println("Board:   " + board);
        System.out.println();
        System.out.println("Hero hand:    " + result.heroHand());
        System.out.println("Villain hand: " + result.villainHand());
        System.out.println("Winner:       " + result.winner());
    }

    private static List<Card> parseCards(String compactCards) {
        if (compactCards == null || compactCards.isBlank()) {
            throw new IllegalArgumentException("Card input must not be blank");
        }

        String cleaned = compactCards.replaceAll("\\s+", "");
        if (cleaned.length() % 2 != 0) {
            throw new IllegalArgumentException("Cards must use two-character notation, e.g. AsKs or QdQc");
        }

        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < cleaned.length(); i += 2) {
            cards.add(Card.parse(cleaned.substring(i, i + 2)));
        }
        return cards;
    }
}
