package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SharedBoardMultiwayShowdownOracleTest {
    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    private static List<WeightedCombo> hands() {
        return List.of(
                combo("AS", "AH", 1),
                combo("KS", "KH", 1),
                combo("QS", "QH", 1),
                combo("JS", "JH", 1),
                combo("TS", "TH", 1),
                combo("9S", "9H", 1));
    }

    @Test
    void oneBoardStreamFeedsEverySubsetAndIsIndependentOfCallOrderOrRangeWeights() {
        var oracle = new SharedBoardMultiwayShowdownOracle(5000, 42);
        var hands = hands();
        var full = oracle.estimate(hands, 0b111111);
        var headsUp = oracle.estimate(hands, 0b000011);
        var threeWay = oracle.estimate(hands, 0b001011);
        assertEquals(5000, oracle.boardsEvaluated());
        assertEquals(5000, full.trials());
        assertEquals(1, sum(full.shares()), 1e-12);
        assertEquals(1, sum(headsUp.shares()), 1e-12);
        assertEquals(1, sum(threeWay.shares()), 1e-12);
        assertEquals(0, headsUp.shares()[5]);
        assertEquals(0, headsUp.standardErrors()[5]);
        assertTrue(full.standardErrors()[0] > 0);

        var reweighted = new ArrayList<>(hands);
        reweighted.set(0, combo("AS", "AH", 50));
        assertArrayEquals(full.shares(), oracle.estimate(reweighted, 0b111111).shares());
        assertEquals(5000, oracle.boardsEvaluated());

        var changed = new ArrayList<>(hands);
        changed.set(5, combo("8S", "8H", 1));
        oracle.estimate(changed, 0b111111);
        assertEquals(10000, oracle.boardsEvaluated());
        assertArrayEquals(full.shares(), oracle.estimate(hands, 0b111111).shares());
        assertEquals(15000, oracle.boardsEvaluated());
    }

    @Test
    void sampledSharesAgreeWithExactBoardEnumerationAcrossDifferentActiveSubsets() {
        var sampled = new SharedBoardMultiwayShowdownOracle(10_000, 711);
        var exact = new ExactMultiwayShowdownOracle();
        var hands = hands();
        for (int mask : List.of(0b000011, 0b001011, 0b101101, 0b111111)) {
            var estimate = sampled.estimate(hands, mask);
            var truth = exact.estimate(hands, mask);
            for (int seat = 0; seat < hands.size(); seat++)
                assertEquals(
                        truth.shares()[seat],
                        estimate.shares()[seat],
                        6 * estimate.standardErrors()[seat] + 0.005);
        }
        assertEquals(10_000, sampled.boardsEvaluated());
    }

    @Test
    void rejectsInvalidDealsAndMasksBeforeSampling() {
        var oracle = new SharedBoardMultiwayShowdownOracle(10, 1);
        assertThrows(
                IllegalArgumentException.class, () -> new SharedBoardMultiwayShowdownOracle(0, 1));
        assertThrows(IllegalArgumentException.class, () -> oracle.estimate(hands(), 1));
        assertThrows(IllegalArgumentException.class, () -> oracle.estimate(hands(), 1 << 6));
        var collision = new ArrayList<>(hands());
        collision.set(5, combo("AS", "8H", 1));
        assertThrows(IllegalArgumentException.class, () -> oracle.estimate(collision, 0b111111));
        assertEquals(0, oracle.boardsEvaluated());
    }

    private static double sum(double[] values) {
        double result = 0;
        for (double value : values) result += value;
        return result;
    }
}
