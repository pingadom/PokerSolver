package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Kind.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Status.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class SixMaxPreflopTerminalPayoffTest {
    private static SixMaxPreflopBetting game() {
        return new SixMaxPreflopBetting(SixMaxPreflopBetting.Rules.reference100Bb());
    }

    private static SixMaxPreflopBetting.Move move(
            PreflopAllInSpot.Seat seat, SixMaxPreflopBetting.Kind kind, double amountBb) {
        return new SixMaxPreflopBetting.Move(seat, kind, amountBb);
    }

    private static List<WeightedCombo> dealt() {
        return List.of(
                combo("AS", "AH"),
                combo("KS", "KH"),
                combo("QS", "QH"),
                combo("JS", "JH"),
                combo("TS", "TH"),
                combo("9S", "9H"));
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }

    @Test
    void documentedFiveBetHistorySettlesFoldAndCalledShoveWithTheSameChips() {
        var betting = game();
        var decision = betting.replay(ValidationSpot.create().priorActions());
        var rule = new CashRakeRule(0.05, 1, true);
        MultiwayShowdownOracle oracle =
                (hands, mask) -> {
                    assertEquals((1 << UTG.ordinal()) | (1 << BTN.ordinal()), mask);
                    return MultiwayShowdownEstimate.certain(new double[] {0.75, 0, 0, 0.25, 0, 0});
                };
        var folded = betting.apply(decision, move(UTG, FOLD, 0));
        var foldPayoff = SixMaxPreflopTerminalPayoff.settle(folded, dealt(), rule, oracle);
        assertEquals(UNCONTESTED, foldPayoff.status());
        assertEquals(0, foldPayoff.rakeBb());
        assertEquals(23.5, foldPayoff.utilitiesBb().get(BTN.ordinal()), 1e-12);
        assertEquals(-22, foldPayoff.utilitiesBb().get(UTG.ordinal()), 1e-12);
        assertEquals(0, sum(foldPayoff.utilitiesBb()), 1e-12);

        var shove = betting.apply(decision, move(UTG, RAISE_TO, 100));
        var called = betting.apply(shove, move(BTN, CALL, 100));
        var payoff = SixMaxPreflopTerminalPayoff.settle(called, dealt(), rule, oracle);
        assertEquals(ALL_IN_SHOWDOWN, payoff.status());
        assertEquals(201.5, called.potBb());
        assertEquals(1, payoff.rakeBb(), 1e-12);
        assertEquals(50.375, payoff.utilitiesBb().get(UTG.ordinal()), 1e-12);
        assertEquals(-49.875, payoff.utilitiesBb().get(BTN.ordinal()), 1e-12);
        assertEquals(-1, sum(payoff.utilitiesBb()), 1e-12);
        assertEquals(0, payoff.maximumPayoffStandardErrorBb());
    }

    @Test
    void sixSeatBettingCanReachThreeWayAllInShowdown() {
        var betting = game();
        var state = betting.apply(betting.initialState(), move(UTG, RAISE_TO, 100));
        state = betting.apply(state, move(HJ, CALL, 100));
        state = betting.apply(state, move(CO, CALL, 100));
        state = betting.apply(state, move(BTN, FOLD, 0));
        state = betting.apply(state, move(SB, FOLD, 0));
        state = betting.apply(state, move(BB, FOLD, 0));
        assertEquals(ALL_IN_SHOWDOWN, state.status());
        assertEquals(301.5, state.potBb());
        var payoff =
                SixMaxPreflopTerminalPayoff.settle(
                        state,
                        dealt(),
                        new CashRakeRule(0.05, 1, true),
                        (hands, mask) -> {
                            assertEquals(0b000111, mask);
                            return MultiwayShowdownEstimate.certain(
                                    new double[] {0.5, 0.3, 0.2, 0, 0, 0});
                        });
        assertEquals(0b000111, payoff.activeMask());
        assertArrayEquals(
                new double[] {50.25, -9.85, -39.9, 0, -0.5, -1},
                payoff.utilitiesBb().stream().mapToDouble(Double::doubleValue).toArray(),
                1e-12);
        assertEquals(1, payoff.rakeBb(), 1e-12);
    }

    @Test
    void exactShowdownUsesAllSixHoleCardsAsBoardBlockers() {
        var betting = game();
        var decision = betting.replay(ValidationSpot.create().priorActions());
        var shove = betting.apply(decision, move(UTG, RAISE_TO, 100));
        var called = betting.apply(shove, move(BTN, CALL, 100));
        var oracle = new ExactMultiwayShowdownOracle();
        var payoff =
                SixMaxPreflopTerminalPayoff.settle(called, dealt(), CashRakeRule.none(), oracle);
        var shares = oracle.estimate(dealt(), payoff.activeMask()).shares();
        assertEquals(
                201.5 * shares[UTG.ordinal()] - 100, payoff.utilitiesBb().get(UTG.ordinal()), 1e-8);
        assertEquals(
                201.5 * shares[BTN.ordinal()] - 100, payoff.utilitiesBb().get(BTN.ordinal()), 1e-8);
        assertEquals(0, sum(payoff.utilitiesBb()), 1e-8);
        assertEquals(0, payoff.maximumPayoffStandardErrorBb());
    }

    @Test
    void sampledLegalActionHistoriesConserveChipsAtEveryResolvedTerminal() {
        var betting = game();
        var random = new Random(42);
        var rule = new CashRakeRule(0.05, 1, true);
        int resolved = 0;
        for (int trial = 0; trial < 300; trial++) {
            var state = betting.initialState();
            int actions = 0;
            while (state.status() == DECISION) {
                var legal = state.legalActions();
                state = betting.apply(state, legal.get(random.nextInt(legal.size())));
                assertTrue(++actions <= 36, "Betting round did not terminate");
            }
            if (state.status() == POSTFLOP_CONTINUATION_REQUIRED) continue;
            resolved++;
            var payoff =
                    SixMaxPreflopTerminalPayoff.settle(
                            state,
                            dealt(),
                            rule,
                            (hands, mask) -> {
                                double[] shares = new double[6];
                                for (int seat = 0; seat < 6; seat++)
                                    if ((mask & (1 << seat)) != 0)
                                        shares[seat] = 1.0 / Integer.bitCount(mask);
                                return MultiwayShowdownEstimate.certain(shares);
                            });
            assertEquals(-payoff.rakeBb(), sum(payoff.utilitiesBb()), 1e-8);
            assertTrue(payoff.rakeBb() >= 0 && payoff.rakeBb() <= 1);
        }
        assertTrue(resolved > 0);
    }

    @Test
    void rejectsUnresolvedHandsAndInvalidPhysicalDeals() {
        var betting = game();
        MultiwayShowdownOracle oracle =
                (hands, mask) -> {
                    throw new AssertionError("No showdown should be requested");
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopTerminalPayoff.settle(
                                betting.initialState(), dealt(), CashRakeRule.none(), oracle));
        var state = betting.initialState();
        for (var seat : List.of(UTG, HJ, CO, BTN, SB))
            state = betting.apply(state, move(seat, CALL, 1));
        var postflop = betting.apply(state, move(BB, CHECK, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopTerminalPayoff.settle(
                                postflop, dealt(), CashRakeRule.none(), oracle));
        var folded = betting.apply(betting.initialState(), move(UTG, FOLD, 0));
        for (var seat : List.of(HJ, CO, BTN, SB))
            folded = betting.apply(folded, move(seat, FOLD, 0));
        var terminal = folded;
        var overlap = new ArrayList<>(dealt());
        overlap.set(1, combo("AS", "KH"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopTerminalPayoff.settle(
                                terminal, overlap, CashRakeRule.none(), oracle));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopTerminalPayoff.settle(
                                terminal, dealt().subList(0, 5), CashRakeRule.none(), oracle));
    }

    private static double sum(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).sum();
    }
}
