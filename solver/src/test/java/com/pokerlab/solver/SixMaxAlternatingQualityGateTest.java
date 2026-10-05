package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class SixMaxAlternatingQualityGateTest {
    @Test
    void requiresConditionalAndParentQualityTogether() {
        var accepted = SixMaxAlternatingQualityGate.assess(0.03, 0.02, 0.05, true, 0.05, 1e-6);
        assertTrue(accepted.accepted());
        assertEquals("ACCEPTED", accepted.status());
        assertEquals(0.01, accepted.parentImprovementBb(), 1e-12);
        var stale = SixMaxAlternatingQualityGate.assess(0.03, 0.02, 0.5, true, 0.05, 1e-6);
        assertFalse(stale.accepted());
        assertTrue(stale.materialParentImprovement());
        assertEquals("CONDITIONAL_TARGET_FAILED", stale.status());
        var regression = SixMaxAlternatingQualityGate.assess(0.02, 0.03, 0.01, true, 0.05, 1e-6);
        assertEquals("PARENT_QUALITY_REGRESSION", regression.status());
        assertFalse(regression.accepted());
    }

    @Test
    void refusesUnreachedBranchesAndNumericalPlateaus() {
        assertEquals(
                "SELECTED_BRANCH_UNREACHED",
                SixMaxAlternatingQualityGate.assess(1, 0.5, 0, false, 0.05, 1e-6).status());
        var plateau =
                SixMaxAlternatingQualityGate.assess(0.02, 0.02 + 1e-10, 0.01, true, 0.05, 1e-6);
        assertTrue(plateau.parentNashConvDidNotIncrease());
        assertFalse(plateau.accepted());
        assertEquals("NO_MATERIAL_IMPROVEMENT", plateau.status());
        assertTrue(SixMaxAlternatingQualityGate.assess(1e-6, 0, 0, true, 0.05, 1e-6).accepted());
    }

    @Test
    void rejectsInvalidScoresAndThresholds() {
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxAlternatingQualityGate.assess(value, 0, 0, true, 0.05, 1e-6));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxAlternatingQualityGate.assess(0, value, 0, true, 0.05, 1e-6));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxAlternatingQualityGate.assess(0, 0, value, true, 0.05, 1e-6));
        }
        for (double value : new double[] {Double.NaN, Double.POSITIVE_INFINITY, 0, -1})
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SixMaxAlternatingQualityGate.assess(0, 0, 0, true, value, 1e-6));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxAlternatingQualityGate.assess(0, 0, 0, true, 0.05, 1e-10));
    }
}
