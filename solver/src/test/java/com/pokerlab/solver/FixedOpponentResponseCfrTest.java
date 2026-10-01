package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
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

    @Test
    void exactRootEnumeratesKuhnDealsIndependentlyOfSeed() {
        var game = new KuhnPoker();
        var baseline = new CfrSolver<>(game, CfrSolver.Variant.VANILLA).solve(1_000);
        var first =
                new FixedOpponentResponseCfr<>(
                                game,
                                baseline,
                                0,
                                42,
                                FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT)
                        .solve(500);
        var second =
                new FixedOpponentResponseCfr<>(
                                game,
                                baseline,
                                0,
                                99,
                                FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT)
                        .solve(500);
        assertEquals(first, second);
        assertEquals(0, first.missingFixedOpponentQueries());
    }

    @Test
    void checkpointsExactlyMatchStandaloneSolvesAndRemainImmutable() {
        var game = new KuhnPoker();
        var baseline = new CfrSolver<>(game, CfrSolver.Variant.VANILLA).solve(1_000);
        for (var mode : FixedOpponentResponseCfr.ChanceMode.values()) {
            var trainer = new FixedOpponentResponseCfr<>(game, baseline, 0, 42, mode);
            var checkpoints = trainer.solveCheckpoints(List.of(40, 100));
            assertEquals(
                    new FixedOpponentResponseCfr<>(game, baseline, 0, 42, mode).solve(40),
                    checkpoints.get(0));
            assertEquals(
                    new FixedOpponentResponseCfr<>(game, baseline, 0, 42, mode).solve(100),
                    checkpoints.get(1));
            trainer.solve(150);
            assertEquals(
                    new FixedOpponentResponseCfr<>(game, baseline, 0, 42, mode).solve(40),
                    checkpoints.get(0));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FixedOpponentResponseCfr<>(game, baseline, 0, 42)
                                .solveCheckpoints(List.of(100, 40)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new FixedOpponentResponseCfr<>(game, baseline, 0, 42)
                                .solveCheckpoints(List.of(40, 40)));
    }

    @Test
    void physicalPublicChanceCheckpointsMatchStandaloneTraining() {
        var game = ButtonBigBlindRangeValidationFixture.createCoarseBucketed();
        var baseline =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                42)
                        .solve(100);
        var checkpoints =
                new FixedOpponentResponseCfr<>(game, baseline, 1, 43)
                        .solveCheckpoints(List.of(50, 100));
        assertEquals(
                new FixedOpponentResponseCfr<>(game, baseline, 1, 43).solve(50),
                checkpoints.get(0));
        assertEquals(
                new FixedOpponentResponseCfr<>(game, baseline, 1, 43).solve(100),
                checkpoints.get(1));
    }
}
