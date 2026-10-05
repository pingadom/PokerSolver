package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxContinuationStudyBudgetTest {
    @Test
    void preflightMatchesIndependentFullWalkWithBlockersAndStackCaps() {
        for (double[] bets :
                List.of(
                        new double[] {3.25, 6.5, 13},
                        new double[] {97, 200, 200},
                        new double[] {20, 80, 100})) {
            var selection =
                    new SixMaxConnectedPreflopGame.Selection(
                            SixMaxConnectedPreflopGameTest.HISTORY,
                            List.of(board("As 2d 3d"), board("2d 3d 4d")),
                            bets[0],
                            bets[1],
                            bets[2]);
            var game =
                    new SixMaxConnectedPreflopGame(
                            SixMaxConnectedPreflopGameTest.base(), List.of(selection));
            var cost = SixMaxContinuationStudyBudget.standard().validate(game);
            assertEquals(3, cost.compatibleDealFlops());
            assertEquals(walk(game, game.initialState()), cost.completeTreeStates());
        }
    }

    @Test
    void exactLimitAdmitsTheGameAndOneLessRejectsBeforeRefinement() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var cost = SixMaxContinuationStudyBudget.standard().validate(game);
        assertEquals(
                cost,
                new SixMaxContinuationStudyBudget(2, cost.completeTreeStates()).validate(game));
        var insufficient = new SixMaxContinuationStudyBudget(2, cost.completeTreeStates() - 1);
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxConditionalPostflopRefinement.refine(
                                        game,
                                        new CfrSolution(1, Map.of()),
                                        1,
                                        insufficient,
                                        b -> fail("Should reject before refining a branch")));
        assertTrue(failure.getMessage().contains("states, exceeding study budget"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxContinuationStudyBudget(1, 2_000_000).validate(game));
        for (int pairs : List.of(0, 17))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxContinuationStudyBudget(pairs, 2_000_000));
        for (long states : List.of(0L, 2_000_001L))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SixMaxContinuationStudyBudget(16, states));
    }

    private static List<Card> board(String cards) {
        return Arrays.stream(cards.split(" ")).map(Card::parse).toList();
    }

    private static long walk(
            SixMaxConnectedPreflopGame game, SixMaxConnectedPreflopGame.State state) {
        if (game.isTerminal(state)) return 1;
        long count = 1;
        if (game.currentPlayer(state) == -1)
            for (var outcome : game.chanceOutcomes(state)) count += walk(game, outcome.state());
        else
            for (String action : game.legalActions(state))
                count += walk(game, game.afterAction(state, action));
        return count;
    }
}
