package com.pokerlab.solver;

/** Showdown equity estimate for one exact preflop combo matchup. */
public record EquityEstimate(double equity, double standardError, int trials) {
    public EquityEstimate {
        if (!Double.isFinite(equity) || equity < 0 || equity > 1)
            throw new IllegalArgumentException("Equity must be in [0, 1]");
        if (!Double.isFinite(standardError) || standardError < 0 || standardError > 0.5)
            throw new IllegalArgumentException("Invalid equity standard error");
        if (trials < 1) throw new IllegalArgumentException("Equity requires at least one trial");
    }
}
