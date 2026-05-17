package com.pokerlab.core.showdown;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShowdownTest {

    @Test
    void heroPairOfAcesBeatsVillainPairOfQueens() {
        ShowdownResult result = Showdown.compare(
                cards("As", "Ks"),
                cards("Qd", "Qc"),
                cards("Ah", "7c", "2s", "9d", "Jc")
        );

        assertEquals(Winner.HERO, result.winner());
        assertEquals(HandCategory.ONE_PAIR, result.heroHand().category());
        assertEquals(HandCategory.ONE_PAIR, result.villainHand().category());
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
