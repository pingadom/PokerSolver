package com.pokerlab.core.batch;

/** Equity shares are unnormalised fractional pots; equity is shares / trials. */
public record PlayerResult(String name, long wins, long ties, long losses, double equityShares) {
    public PlayerResult {
        if (name == null
                || name.isBlank()
                || wins < 0
                || ties < 0
                || losses < 0
                || !Double.isFinite(equityShares)
                || equityShares < wins
                || equityShares > wins + (double) ties)
            throw new IllegalArgumentException("invalid player result");
    }

    public double equity() {
        return equityShares / (wins + ties + losses);
    }
}
