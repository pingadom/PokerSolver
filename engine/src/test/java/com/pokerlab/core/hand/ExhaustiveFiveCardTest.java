package com.pokerlab.core.hand;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Deck;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExhaustiveFiveCardTest {
    /** Independent combinatorial category frequencies across all C(52,5) hands. */
    @Test
    void allFiveCardHandsMatchKnownCategoryFrequencies() {
        var deck = new Deck().cards();
        var counts = new EnumMap<HandCategory, Long>(HandCategory.class);
        for (int a = 0; a < 48; a++)
            for (int b = a + 1; b < 49; b++)
                for (int c = b + 1; c < 50; c++)
                    for (int d = c + 1; d < 51; d++)
                        for (int e = d + 1; e < 52; e++) {
                            var rank =
                                    HandEvaluator.evaluateFive(
                                                    List.of(
                                                            deck.get(a),
                                                            deck.get(b),
                                                            deck.get(c),
                                                            deck.get(d),
                                                            deck.get(e)))
                                            .category();
                            counts.merge(rank, 1L, Long::sum);
                        }
        assertEquals(2_598_960, counts.values().stream().mapToLong(Long::longValue).sum());
        assertEquals(
                Map.of(
                        HandCategory.HIGH_CARD, 1_302_540L,
                        HandCategory.ONE_PAIR, 1_098_240L,
                        HandCategory.TWO_PAIR, 123_552L,
                        HandCategory.THREE_OF_A_KIND, 54_912L,
                        HandCategory.STRAIGHT, 10_200L,
                        HandCategory.FLUSH, 5_108L,
                        HandCategory.FULL_HOUSE, 3_744L,
                        HandCategory.FOUR_OF_A_KIND, 624L,
                        HandCategory.STRAIGHT_FLUSH, 36L,
                        HandCategory.ROYAL_FLUSH, 4L),
                counts);
    }
}
