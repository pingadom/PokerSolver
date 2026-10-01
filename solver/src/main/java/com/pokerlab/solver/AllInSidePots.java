package com.pokerlab.solver;

import java.util.Arrays;
import java.util.Objects;
import java.util.function.IntFunction;

/** Settles all-in commitments by contribution tier, including folded chips and uncalled excess. */
final class AllInSidePots {
    record Result(double[] utilitiesBb, double maximumStandardErrorBb, double rakeBb) {}

    private AllInSidePots() {}

    static Result settle(
            double[] commitments,
            int activeMask,
            double deadMoneyBb,
            IntFunction<MultiwayShowdownEstimate> showdown) {
        return settle(commitments, activeMask, deadMoneyBb, showdown, CashRakeRule.none(), false);
    }

    static Result settle(
            double[] commitments,
            int activeMask,
            double deadMoneyBb,
            IntFunction<MultiwayShowdownEstimate> showdown,
            CashRakeRule rakeRule,
            boolean flopDealt) {
        Objects.requireNonNull(commitments, "commitments");
        Objects.requireNonNull(showdown, "showdown");
        Objects.requireNonNull(rakeRule, "rakeRule");
        if (commitments.length < 2
                || commitments.length > 6
                || (activeMask & ~((1 << commitments.length) - 1)) != 0
                || activeMask == 0
                || !Double.isFinite(deadMoneyBb)
                || deadMoneyBb < 0)
            throw new IllegalArgumentException("Invalid all-in settlement inputs");
        double[] levels =
                Arrays.stream(commitments).distinct().filter(value -> value > 0).sorted().toArray();
        if (levels.length == 0)
            throw new IllegalArgumentException("At least one player must have committed chips");
        double[] utilities = new double[commitments.length];
        double[] errorBounds = new double[commitments.length];
        double rake = 0;
        for (int seat = 0; seat < commitments.length; seat++) {
            double amount = commitments[seat];
            if (!Double.isFinite(amount) || amount < 0)
                throw new IllegalArgumentException("Invalid chip commitment");
            utilities[seat] = amount == 0 ? 0 : -amount;
        }
        double lower = 0;
        for (double upper : levels) {
            int contributors = 0;
            int eligible = 0;
            for (int seat = 0; seat < commitments.length; seat++) {
                if (commitments[seat] < upper) continue;
                contributors++;
                if ((activeMask & (1 << seat)) != 0) eligible |= 1 << seat;
            }
            if (eligible == 0)
                throw new IllegalArgumentException("A side pot has no eligible player");
            double grossPot = (upper - lower) * contributors + (lower == 0 ? deadMoneyBb : 0);
            double tierRake =
                    rakeRule.forTier(grossPot, contributors, flopDealt, rakeRule.capBb() - rake);
            rake += tierRake;
            double pot = grossPot - tierRake;
            if (Integer.bitCount(eligible) == 1) {
                utilities[Integer.numberOfTrailingZeros(eligible)] += pot;
            } else {
                MultiwayShowdownEstimate estimate = showdown.apply(eligible);
                if (estimate == null)
                    throw new IllegalArgumentException("Missing side-pot showdown estimate");
                double[] shares = estimate.shares();
                double[] errors = estimate.standardErrors();
                if (shares.length != commitments.length)
                    throw new IllegalArgumentException("Wrong side-pot share count");
                for (int seat = 0; seat < commitments.length; seat++) {
                    utilities[seat] += pot * shares[seat];
                    // Multiple tiers use the same boards; summing SEs is conservative.
                    errorBounds[seat] += pot * errors[seat];
                }
            }
            lower = upper;
        }
        double expected = deadMoneyBb - rake;
        double actual = Arrays.stream(utilities).sum();
        if (Math.abs(actual - expected) > 1e-8)
            throw new IllegalStateException("All-in settlement did not conserve chips");
        return new Result(utilities, Arrays.stream(errorBounds).max().orElse(0), rake);
    }
}
