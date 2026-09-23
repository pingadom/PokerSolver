package com.pokerlab.solver;

/** Pot shares and their per-seat sampling standard errors for one active subset. */
public record MultiwayShowdownEstimate(double[] shares, double[] standardErrors, long trials) {
    public MultiwayShowdownEstimate {
        if (shares == null
                || standardErrors == null
                || shares.length != standardErrors.length
                || trials < 0) throw new IllegalArgumentException("Invalid showdown estimate");
        shares = shares.clone();
        standardErrors = standardErrors.clone();
    }

    @Override
    public double[] shares() {
        return shares.clone();
    }

    @Override
    public double[] standardErrors() {
        return standardErrors.clone();
    }

    /** For test fixtures or independently exact payoff inputs. */
    public static MultiwayShowdownEstimate certain(double[] shares) {
        return new MultiwayShowdownEstimate(shares, new double[shares.length], 0);
    }
}
