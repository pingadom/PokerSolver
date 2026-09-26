package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StrategyCompletionTest {
    @Test
    void fillsUnseenKuhnInformationSetsForExactBestResponseChecks() {
        KuhnPoker game = new KuhnPoker();
        CfrSolution sparse =
                new CfrSolver<>(game, CfrSolver.Variant.VANILLA, CfrSolver.ChanceMode.SAMPLED, 42)
                        .solve(1);
        var result = StrategyCompletion.uniformAtUnseen(game, sparse, 1_000);
        assertEquals(12, result.solution().strategy().size());
        assertTrue(result.addedInformationSets() > 0);
        assertTrue(Double.isFinite(HeadsUpBestResponse.assess(game, result.solution()).gap()));
        assertThrows(
                IllegalArgumentException.class,
                () -> StrategyCompletion.uniformAtUnseen(game, sparse, 2));
        Map<String, Map<String, Double>> foreign = new HashMap<>(sparse.strategy());
        foreign.put("0:foreign", Map.of("check", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> StrategyCompletion.uniformAtUnseen(game, new CfrSolution(1, foreign), 1_000));
    }
}
