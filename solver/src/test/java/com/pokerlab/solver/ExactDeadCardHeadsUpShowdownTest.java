package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.*;
import org.junit.jupiter.api.Test;

class ExactDeadCardHeadsUpShowdownTest {
    static List<WeightedCombo> hands() {
        return List.of("4c 4d", "9d Kd", "Ah Kh", "8h 8s", "5c 6c", "7d 7h").stream()
                .map(
                        s ->
                                new WeightedCombo(
                                        Card.parse(s.substring(0, 2)),
                                        Card.parse(s.substring(3)),
                                        1))
                .toList();
    }

    @Test
    void exactCountsMatchIndependentAllSubsetsEnumerationForDifferentSeatPairs() {
        var independent = new ExactMultiwayShowdownOracle();
        for (int mask : List.of(12, 36, 40)) {
            var counts = ExactDeadCardHeadsUpShowdown.enumerate(hands(), mask);
            var expected = independent.estimate(hands(), mask);
            assertEquals(658008, counts.boards());
            assertEquals(1316016, counts.handEvaluations());
            assertEquals(counts.boards(), counts.firstWins() + counts.secondWins() + counts.ties());
            assertArrayEquals(expected.shares(), counts.estimate().shares(), 1e-12);
            assertArrayEquals(new double[6], counts.estimate().standardErrors());
            assertEquals(1, Arrays.stream(counts.estimate().shares()).sum(), 1e-12);
            counts.estimate().shares()[counts.firstSeat()] = -1;
            assertTrue(counts.estimate().shares()[counts.firstSeat()] >= 0);
        }
    }

    @Test
    void weightsDoNotAffectPhysicalCountsAndFoldedCardsDo() {
        var first = ExactDeadCardHeadsUpShowdown.enumerate(hands(), 36);
        var weighted =
                hands().stream().map(h -> new WeightedCombo(h.first(), h.second(), 17)).toList();
        assertEquals(first, ExactDeadCardHeadsUpShowdown.enumerate(weighted, 36));
        var changed = new ArrayList<>(hands());
        changed.set(1, new WeightedCombo(Card.parse("2d"), Card.parse("3d"), 1));
        var other = ExactDeadCardHeadsUpShowdown.enumerate(changed, 36);
        assertNotEquals(first.firstWins(), other.firstWins());
        assertEquals(first.boards(), other.boards());
    }

    @Test
    void rejectsWrongMasksCollisionsAndFabricatedCounts() {
        for (int mask : List.of(0, 4, 7, 64, -1))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> ExactDeadCardHeadsUpShowdown.enumerate(hands(), mask));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactDeadCardHeadsUpShowdown.enumerate(hands().subList(0, 5), 3));
        var collision = new ArrayList<>(hands());
        collision.set(0, hands().get(5));
        assertThrows(
                IllegalArgumentException.class,
                () -> ExactDeadCardHeadsUpShowdown.enumerate(collision, 36));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExactDeadCardHeadsUpShowdown.Counts(2, 5, 0, 0, 0, 658008, 1316016));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ExactDeadCardHeadsUpShowdown.Counts(2, 5, 658008, 0, 0, 658008, 1));
    }
}
