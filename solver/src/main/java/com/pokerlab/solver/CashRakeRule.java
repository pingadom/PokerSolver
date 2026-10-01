package com.pokerlab.solver;

/**
 * Explicit research cash-game rake assumption, expressed in big blinds. A positive rake fraction
 * applies to called pot tiers until the per-hand cap is exhausted. Uncalled excess is never raked.
 * This is a configurable payoff rule, not a claim about any particular card room's schedule.
 */
public record CashRakeRule(double fraction, double capBb, boolean noFlopNoDrop) {
    public CashRakeRule {
        if (!Double.isFinite(fraction)
                || fraction < 0
                || fraction >= 1
                || !Double.isFinite(capBb)
                || capBb < 0)
            throw new IllegalArgumentException(
                    "Rake fraction must be in [0,1) and cap nonnegative");
    }

    public static CashRakeRule none() {
        return new CashRakeRule(0, 0, true);
    }

    double forTier(double potBb, int contributors, boolean flopDealt, double remainingCapBb) {
        if (contributors < 2 || (noFlopNoDrop && !flopDealt)) return 0;
        return Math.min(fraction * potBb, Math.max(0, remainingCapBb));
    }
}
