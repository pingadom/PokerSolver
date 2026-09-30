package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FiniteChanceResponseCalibrationTest {
    @Test
    void candidateValuesStayBelowExactBestResponseForBothPlayersOnPhysicalSubgame() {
        var physical = ButtonBigBlindRangeValidationFixture.createCoarseBucketed();
        var game =
                new PhysicalDeckChanceSubgame(physical, List.of(0.2), List.of(0.4), List.of(0.6));
        var baseline = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(50);
        for (int target = 0; target <= 1; target++) {
            var report =
                    FiniteChanceResponseCalibration.assess(
                            game, baseline, target, List.of(50, 200), List.of(42L));
            assertEquals(2, report.candidates().size());
            assertTrue(report.exactBestResponseGainBb() >= -1e-8);
            for (var candidate : report.candidates()) {
                assertTrue(Double.isFinite(candidate.candidateGainBb()));
                assertTrue(candidate.shortfallToExactBb() >= 0);
                assertTrue(Double.isFinite(candidate.finalRegretGainBb()));
                assertTrue(candidate.finalRegretShortfallBb() >= 0);
                assertEquals(0, candidate.missingFixedOpponentQueries());
                assertTrue(candidate.baselineTargetKeysWithoutResponse() >= 0);
            }
            var exactRoot =
                    FiniteChanceResponseCalibration.assess(
                            game,
                            baseline,
                            target,
                            List.of(50),
                            List.of(42L),
                            FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT);
            assertEquals(
                    FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT,
                    exactRoot.candidates().getFirst().chanceMode());
            assertTrue(exactRoot.candidates().getFirst().shortfallToExactBb() >= 0);
            assertEquals(0, exactRoot.candidates().getFirst().missingFixedOpponentQueries());
        }
    }

    @Test
    void rejectsInvalidInputsBeforeEnumerating() {
        var game = new KuhnPoker();
        var baseline = new CfrSolver<>(game, CfrSolver.Variant.VANILLA).solve(10);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteChanceResponseCalibration.assess(
                                game, baseline, 2, List.of(10), List.of(42L)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteChanceResponseCalibration.assess(
                                game, baseline, 0, List.of(0), List.of(42L)));
    }
}
