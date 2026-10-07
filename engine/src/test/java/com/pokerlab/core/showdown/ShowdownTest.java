package com.pokerlab.core.showdown;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.hand.HandCategory;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShowdownTest {

    @Test
    void sharedTripsUseBothKickersAndStillAllowRealTies() {
        var board = cards("2c", "2d", "3h", "Ac", "2h");
        var strong = cards("Kd", "9d");
        var weak = cards("6c", "5c");
        assertEquals(Winner.HERO, Showdown.compare(strong, weak, board).winner());
        assertEquals(Winner.VILLAIN, Showdown.compare(weak, strong, board).winner());
        assertEquals(Winner.TIE, Showdown.compare(strong, cards("Kh", "8d"), board).winner());
    }

    @Test
    void heroPairOfAcesBeatsVillainPairOfQueens() {
        ShowdownResult result =
                Showdown.compare(
                        cards("As", "Ks"), cards("Qd", "Qc"), cards("Ah", "7c", "2s", "9d", "Jc"));

        assertEquals(Winner.HERO, result.winner());
        assertEquals(HandCategory.ONE_PAIR, result.heroHand().category());
        assertEquals(HandCategory.ONE_PAIR, result.villainHand().category());
    }

    private static List<Card> cards(String... values) {
        return java.util.Arrays.stream(values).map(Card::parse).toList();
    }
}
