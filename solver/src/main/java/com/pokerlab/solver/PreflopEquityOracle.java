package com.pokerlab.solver;

@FunctionalInterface
public interface PreflopEquityOracle {
    EquityEstimate estimate(WeightedCombo first, WeightedCombo second);
}
