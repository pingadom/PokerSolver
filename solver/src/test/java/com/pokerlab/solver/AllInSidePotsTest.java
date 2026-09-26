package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AllInSidePotsTest {
    @Test
    void splitsMainAndSidePotsAndReturnsUncalledExcess() {
        var result =
                AllInSidePots.settle(
                        new double[] {30, 10, 20},
                        0b111,
                        0.5,
                        mask ->
                                switch (mask) {
                                    case 0b111 ->
                                            new MultiwayShowdownEstimate(
                                                    new double[] {0, 0.5, 0.5},
                                                    new double[] {0, 0.1, 0.1},
                                                    100);
                                    case 0b101 ->
                                            new MultiwayShowdownEstimate(
                                                    new double[] {0.5, 0, 0.5},
                                                    new double[] {0.1, 0, 0.1},
                                                    100);
                                    default -> throw new AssertionError("Unexpected eligible mask");
                                });
        assertArrayEquals(new double[] {-10, 5.25, 5.25}, result.utilitiesBb(), 1e-12);
        assertEquals(5.05, result.maximumStandardErrorBb(), 1e-12);
    }

    @Test
    void foldedBlindStaysInTheMainPot() {
        var result =
                AllInSidePots.settle(
                        new double[] {30, 10, 2},
                        0b011,
                        0,
                        mask -> MultiwayShowdownEstimate.certain(new double[] {0, 1, 0}));
        assertArrayEquals(new double[] {-10, 12, -2}, result.utilitiesBb(), 1e-12);
    }
}
