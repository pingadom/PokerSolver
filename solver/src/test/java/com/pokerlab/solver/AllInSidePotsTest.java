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

    @Test
    void rakesCalledMainAndSidePotsUpToOneHandCapButReturnsUncalledExcess() {
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
                                },
                        new CashRakeRule(0.05, 2, true),
                        true);
        assertEquals(2, result.rakeBb(), 1e-12);
        assertArrayEquals(new double[] {-10.2375, 4.4875, 4.25}, result.utilitiesBb(), 1e-12);
        assertEquals(4.85, result.maximumStandardErrorBb(), 1e-12);
    }

    @Test
    void noFlopNoDropLeavesUncontestedPotUntouched() {
        var commitments = new double[] {10, 1};
        var rule = new CashRakeRule(0.05, 1, true);
        var unraked =
                AllInSidePots.settle(
                        commitments,
                        0b01,
                        0,
                        mask -> {
                            throw new AssertionError("Uncontested hand needs no showdown");
                        },
                        rule,
                        false);
        assertArrayEquals(new double[] {1, -1}, unraked.utilitiesBb(), 1e-12);
        assertEquals(0, unraked.rakeBb());
        var raked =
                AllInSidePots.settle(
                        commitments,
                        0b01,
                        0,
                        mask -> {
                            throw new AssertionError("Uncontested hand needs no showdown");
                        },
                        new CashRakeRule(0.05, 1, false),
                        false);
        assertArrayEquals(new double[] {0.9, -1}, raked.utilitiesBb(), 1e-12);
        assertEquals(0.1, raked.rakeBb(), 1e-12);
    }

    @Test
    void rejectsInvalidRakeSchedules() {
        assertThrows(IllegalArgumentException.class, () -> new CashRakeRule(-0.01, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new CashRakeRule(1, 1, true));
        assertThrows(IllegalArgumentException.class, () -> new CashRakeRule(Double.NaN, 1, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new CashRakeRule(0.05, Double.POSITIVE_INFINITY, true));
    }
}
