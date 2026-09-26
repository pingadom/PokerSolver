package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExactMultiwayShowdownOracleTest {
    private static WeightedCombo combo(String a, String b) {
        return new WeightedCombo(Card.parse(a), Card.parse(b), 1);
    }

    @Test
    void agreesWithIndependentHeadsUpEnumerator() {
        var hands = List.of(combo("As", "Ah"), combo("Ks", "Kh"));
        var expected = new ExactPreflopEquityOracle().estimate(hands.get(0), hands.get(1));
        var actual = new ExactMultiwayShowdownOracle().estimate(hands, 3);
        assertEquals(expected.equity(), actual.shares()[0], 1e-12);
        assertEquals(1712304, actual.trials());
        assertArrayEquals(new double[2], actual.standardErrors());
    }

    @Test
    void allSubsetsUseFoldedCardRemovalAndConserveShares() {
        var hands =
                List.of(
                        combo("As", "Ah"),
                        combo("Ks", "Kh"),
                        combo("Qs", "Qh"),
                        combo("Js", "Jh"),
                        combo("Ts", "Th"),
                        combo("9s", "9h"));
        var oracle = new ExactMultiwayShowdownOracle();
        for (int mask = 1; mask < 64; mask++) {
            if (Integer.bitCount(mask) < 2) continue;
            var result = oracle.estimate(hands, mask);
            assertEquals(658008, result.trials()); // C(40, 5), even if only two remain active.
            assertEquals(1, Arrays.stream(result.shares()).sum(), 1e-10);
            assertArrayEquals(new double[6], result.standardErrors());
            for (int p = 0; p < 6; p++)
                if ((mask & (1 << p)) == 0) assertEquals(0, result.shares()[p]);
        }
        var cached = oracle.estimate(hands, 63);
        assertSame(
                cached,
                oracle.estimate(
                        hands.stream()
                                .map(c -> new WeightedCombo(c.first(), c.second(), 2))
                                .toList(),
                        63));
        double share = cached.shares()[0];
        cached.shares()[0] = -1;
        assertEquals(share, oracle.estimate(hands, 63).shares()[0]);
        assertThrows(IllegalArgumentException.class, () -> oracle.estimate(hands, 1));
        assertThrows(IllegalArgumentException.class, () -> oracle.estimate(hands, 65));
        assertThrows(
                IllegalArgumentException.class,
                () -> oracle.estimate(List.of(hands.get(0), hands.get(0)), 3));
    }
}
