package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Bridges a completed six-seat betting round to per-seat chip payoffs. All-in showdowns use a
 * physical-deal showdown oracle; unresolved postflop pots deliberately have no terminal value.
 */
public final class SixMaxPreflopTerminalPayoff {
    public record Result(
            SixMaxPreflopBetting.Status status,
            List<Double> utilitiesBb,
            double rakeBb,
            double maximumPayoffStandardErrorBb,
            int activeMask) {
        public Result {
            utilitiesBb = List.copyOf(utilitiesBb);
        }
    }

    private SixMaxPreflopTerminalPayoff() {}

    /**
     * `dealtBySeat` contains all six physical hole-card combinations in UTG-to-BB order, including
     * folded hands; their range weights are irrelevant once this deal is fixed.
     */
    public static Result settle(
            SixMaxPreflopBetting.State state,
            List<WeightedCombo> dealtBySeat,
            CashRakeRule rakeRule,
            MultiwayShowdownOracle showdown) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(rakeRule, "rakeRule");
        Objects.requireNonNull(showdown, "showdown");
        if (state.status() != SixMaxPreflopBetting.Status.UNCONTESTED
                && state.status() != SixMaxPreflopBetting.Status.ALL_IN_SHOWDOWN)
            throw new IllegalArgumentException("Betting round has no terminal preflop payoff");
        validateDeal(dealtBySeat);
        double[] committed = new double[Seat.values().length];
        int activeMask = 0;
        for (Seat seat : Seat.values()) {
            int index = seat.ordinal();
            committed[index] = state.committedBb(seat);
            if (!state.isFolded(seat)) activeMask |= 1 << index;
        }
        if (state.status() == SixMaxPreflopBetting.Status.UNCONTESTED
                ? Integer.bitCount(activeMask) != 1
                : Integer.bitCount(activeMask) < 2)
            throw new IllegalStateException("Terminal status disagrees with live seats");
        var settlement =
                AllInSidePots.settle(
                        committed,
                        activeMask,
                        0,
                        mask -> showdown.estimate(dealtBySeat, mask),
                        rakeRule,
                        state.status() == SixMaxPreflopBetting.Status.ALL_IN_SHOWDOWN);
        List<Double> utilities = new ArrayList<>(committed.length);
        for (double value : settlement.utilitiesBb()) utilities.add(value);
        return new Result(
                state.status(),
                utilities,
                settlement.rakeBb(),
                settlement.maximumStandardErrorBb(),
                activeMask);
    }

    private static void validateDeal(List<WeightedCombo> dealtBySeat) {
        if (dealtBySeat == null || dealtBySeat.size() != Seat.values().length)
            throw new IllegalArgumentException("Exactly six dealt hands are required");
        Set<Card> used = new HashSet<>();
        for (WeightedCombo combo : dealtBySeat) {
            if (combo == null || !used.add(combo.first()) || !used.add(combo.second()))
                throw new IllegalArgumentException("Dealt hands overlap or are missing");
        }
    }
}
