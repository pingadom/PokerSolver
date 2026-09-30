package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FixedOpponentResponseCfrTest {
    @Test
    void approachesBothExactKuhnBestResponseBoundsWithoutPrivateCardLeakage() {
        var game = new KuhnPoker();
        var baseline = new CfrSolver<>(game, CfrSolver.Variant.VANILLA).solve(1_000);
        var exact = HeadsUpBestResponse.assess(game, baseline);
        for (int target = 0; target <= 1; target++) {
            int player = target;
            var trainer = new FixedOpponentResponseCfr<>(game, baseline, target, 42);
            var trained = trainer.solve(20_000);
            assertEquals(
                    trained,
                    new FixedOpponentResponseCfr<>(game, baseline, target, 42).solve(20_000));
            assertEquals(0, trained.missingFixedOpponentQueries());
            assertTrue(
                    trained.response().strategy().keySet().stream()
                            .allMatch(key -> key.startsWith(player + ":")));
            Map<String, Map<String, Double>> merged = new HashMap<>(baseline.strategy());
            merged.putAll(trained.response().strategy());
            double value = StrategyEvaluator.playerZeroUtility(game, new CfrSolution(1, merged));
            if (target == 0) {
                assertTrue(value <= exact.firstBestResponse() + 1e-9);
                assertTrue(exact.firstBestResponse() - value < 0.08);
            } else {
                assertTrue(value >= exact.secondBestResponse() - 1e-9);
                assertTrue(value - exact.secondBestResponse() < 0.08);
            }
        }
    }

    @Test
    void countsExplicitUniformOpponentFallback() {
        var game = new KuhnPoker();
        var sparse = new CfrSolution(1, Map.of());
        var result = new FixedOpponentResponseCfr<>(game, sparse, 0, 42).solve(100);
        assertTrue(result.missingFixedOpponentQueries() > 0);
        assertEquals(result.fixedOpponentQueries(), result.missingFixedOpponentQueries());
        assertEquals(
                result.queriedFixedOpponentInformationSets(),
                result.missingFixedOpponentInformationSets());
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedOpponentResponseCfr<>(game, sparse, 2, 42));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FixedOpponentResponseCfr<>(game, sparse, 0, 42).solve(0));
    }
}
