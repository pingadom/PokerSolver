package com.pokerlab.core.simulation;

/**
 * Small Phase 2 preparation record for storing the result of an equity calculation.
 *
 * Equity is counted as:
 *   (wins + 0.5 * ties) / simulations
 *
 * This class does not run simulations itself. It is just a clean value object that a
 * future MonteCarloSimulator or ExactEnumerator can return.
 */
public record EquityResult(
        long heroWins,
        long villainWins,
        long ties,
        long simulations,
        long runtimeMillis
) {

    public EquityResult {
        if (heroWins < 0 || villainWins < 0 || ties < 0) {
            throw new IllegalArgumentException("win/tie counts must not be negative");
        }
        if (simulations <= 0) {
            throw new IllegalArgumentException("simulations must be positive");
        }
        if (runtimeMillis < 0) {
            throw new IllegalArgumentException("runtimeMillis must not be negative");
        }
        long counted = heroWins + villainWins + ties;
        if (counted != simulations) {
            throw new IllegalArgumentException(
                    "heroWins + villainWins + ties must equal simulations: " + counted + " != " + simulations
            );
        }
    }

    public double heroEquity() {
        return (heroWins + 0.5 * ties) / simulations;
    }

    public double villainEquity() {
        return (villainWins + 0.5 * ties) / simulations;
    }

    public double tieRate() {
        return (double) ties / simulations;
    }

    public double heroWinRate() {
        return (double) heroWins / simulations;
    }

    public double villainWinRate() {
        return (double) villainWins / simulations;
    }
}
