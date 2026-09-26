package com.pokerlab.solver;

import java.util.List;

/** Offline showdown shares, including split pots, for an all-in subset of dealt players. */
@FunctionalInterface
public interface MultiwayShowdownOracle {
    /**
     * Returns one pot share per dealt seat. Bit i of activeMask marks a showdown player. Cards of
     * folded seats stay removed from the board deck because they were physically dealt.
     */
    MultiwayShowdownEstimate estimate(List<WeightedCombo> dealt, int activeMask);
}
