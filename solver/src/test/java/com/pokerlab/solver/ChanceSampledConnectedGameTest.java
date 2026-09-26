package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ChanceSampledConnectedGameTest {
    @Test
    void sampledPublicCardsCanBeAuditedAgainstTheCompleteBoundedGame() {
        ButtonBigBlindContinuationGame game = ButtonBigBlindResearchFixture.create(1, 2);
        CfrSolver<ButtonBigBlindContinuationGame.State> solver =
                new CfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                        42);
        CfrSolution sparse = solver.solve(300);
        assertEquals(sparse, solver.solve(300));
        StrategyCompletion.Result completed =
                StrategyCompletion.uniformAtUnseen(game, sparse, 100_000);
        assertEquals(6_684, completed.solution().strategy().size());
        assertTrue(Double.isFinite(HeadsUpBestResponse.assess(game, completed.solution()).gap()));
    }
}
