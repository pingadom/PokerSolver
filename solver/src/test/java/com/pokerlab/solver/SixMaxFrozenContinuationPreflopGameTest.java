package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxFrozenContinuationPreflopGameTest {
    @Test
    void integratesPhysicalContinuationsExactlyUnderDifferentPreflopPolicies() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var uniform =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                game, new CfrSolution(1, Map.of()), 2_000_000)
                        .solution();
        var projected =
                new SixMaxFrozenContinuationPreflopGame(
                        game, uniform, SixMaxContinuationStudyBudget.standard());
        assertEquals(
                uniform.strategy().keySet().stream().filter(k -> !k.contains(":postflop:")).count(),
                SixMaxPreflopContinuationFeedback.preflopPolicy(uniform).strategy().size());
        assertEquals(
                game.source().chanceOutcomes(game.source().initialState()),
                projected.chanceOutcomes(projected.initialState()));
        assertEquals(
                List.of(.25, .75),
                projected.chanceOutcomes(projected.initialState()).stream()
                        .map(ChanceOutcome::probability)
                        .toList());
        for (var pre :
                List.of(
                        SixMaxPreflopContinuationFeedback.preflopPolicy(uniform),
                        new MultiPlayerCfrSolver<>(projected, CfrSolver.Variant.CFR_PLUS)
                                .solve(4))) {
            var lifted = SixMaxPreflopContinuationFeedback.liftPreflop(uniform, pre);
            assertArrayEquals(
                    MultiPlayerStrategyEvaluator.utilities(game, lifted),
                    MultiPlayerStrategyEvaluator.utilities(projected, pre),
                    1e-12);
        }
        for (var value : projected.terminalValues()) {
            var state =
                    new SixMaxPreflopCheckdownGame.State(value.dealIndex(), value.publicHistory());
            var expected =
                    MultiPlayerStrategyEvaluator.utilitiesFrom(
                            game,
                            uniform,
                            game.replayPreflop(
                                    game.selections().getFirst().history(), value.dealIndex()));
            assertArrayEquals(expected, projected.terminalUtilities(state), 1e-12);
            var copy = projected.terminalUtilities(state);
            copy[0] = 10000;
            assertArrayEquals(expected, projected.terminalUtilities(state), 0);
            assertThrows(
                    UnsupportedOperationException.class, () -> value.utilitiesBb().set(0, 0.0));
        }
    }

    @Test
    void retainsZeroReachDealsAndBlockedFlopsWithoutPosteriorReweighting() {
        var game =
                new SixMaxConnectedPreflopGame(
                        SixMaxConnectedPreflopGameTest.base(),
                        List.of(SixMaxConnectedPreflopGameTest.selection("As 2d 3d")));
        var rows =
                new LinkedHashMap<>(
                        SixMaxConditionalPostflopRefinementTest.uniform(game.source()).strategy());
        for (var root : game.source().chanceOutcomes(game.source().initialState())) {
            var state = root.state();
            var weights = new LinkedHashMap<String, Double>();
            String chosen = state.dealIndex() == 0 ? "call" : "fold";
            for (var action : game.source().legalActions(state))
                weights.put(action, action.equals(chosen) ? 1.0 : 0.0);
            rows.put(
                    game.source().currentPlayer(state) + ":" + game.source().informationSet(state),
                    weights);
        }
        var original = SixMaxConnectedPreflopAudit.liftCheckdown(game, new CfrSolution(1, rows));
        var projected =
                new SixMaxFrozenContinuationPreflopGame(
                        game, original, SixMaxContinuationStudyBudget.standard());
        assertEquals(2, projected.terminalValues().size());
        assertEquals(
                List.of(.25, .75),
                projected.chanceOutcomes(projected.initialState()).stream()
                        .map(ChanceOutcome::probability)
                        .toList());
        // Deal zero blocks As, but is still represented even though its preflop reach is zero.
        for (var value : projected.terminalValues()) {
            var state =
                    new SixMaxPreflopCheckdownGame.State(value.dealIndex(), value.publicHistory());
            assertArrayEquals(
                    game.source().terminalUtilities(state),
                    projected.terminalUtilities(state),
                    1e-12);
        }
        var alternate = SixMaxConditionalPostflopRefinementTest.uniform(game.source());
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(
                        game, SixMaxPreflopContinuationFeedback.liftPreflop(original, alternate)),
                MultiPlayerStrategyEvaluator.utilities(projected, alternate),
                1e-12);
    }

    @Test
    void keysPayoffsByPublicHistoryAsWellAsPrivateDeal() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var raised = SixMaxConnectedPreflopGameTest.selection("2d 3d 4d");
        var limpHistory =
                raised.history().stream()
                        .map(
                                a ->
                                        a.action().equals("raise:3.0")
                                                ? new SixMaxPreflopResearchTrainer.PublicAction(
                                                        a.seat(), "call")
                                                : a.action().equals("call")
                                                        ? new SixMaxPreflopResearchTrainer
                                                                .PublicAction(a.seat(), "check")
                                                        : a)
                        .toList();
        var limped =
                new SixMaxConnectedPreflopGame.Selection(limpHistory, raised.flops(), 1.25, 2.5, 5);
        var game = new SixMaxConnectedPreflopGame(base, List.of(raised, limped));
        var policy =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                                game, new CfrSolution(1, Map.of()), 2_000_000)
                        .solution();
        var projected =
                new SixMaxFrozenContinuationPreflopGame(
                        game, policy, SixMaxContinuationStudyBudget.standard());
        assertEquals(4, projected.terminalValues().size());
        var raiseState = game.replayPreflop(raised.history(), 0).preflop();
        var limpState = game.replayPreflop(limped.history(), 0).preflop();
        assertNotEquals(raiseState.publicHistory(), limpState.publicHistory());
        assertNotEquals(
                projected.terminalUtilities(raiseState)[3],
                projected.terminalUtilities(limpState)[3]);
        for (var selection : game.selections())
            for (var root : base.chanceOutcomes(base.initialState())) {
                var state = game.replayPreflop(selection.history(), root.state().dealIndex());
                assertArrayEquals(
                        MultiPlayerStrategyEvaluator.utilitiesFrom(game, policy, state),
                        projected.terminalUtilities(state.preflop()),
                        1e-12);
            }
    }

    @Test
    void rejectsIncompletePolicyAndOverBudgetTreesBeforeIntegrating() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var original =
                SixMaxConnectedPreflopAudit.liftCheckdown(
                        game, SixMaxConditionalPostflopRefinementTest.uniform(game.source()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxFrozenContinuationPreflopGame(
                                game,
                                SixMaxPreflopContinuationFeedback.preflopPolicy(original),
                                SixMaxContinuationStudyBudget.standard()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxFrozenContinuationPreflopGame(
                                game, original, new SixMaxContinuationStudyBudget(1, 2_000_000)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxFrozenContinuationPreflopGame(
                                game, original, new SixMaxContinuationStudyBudget(8, 1)));
        var rows = new LinkedHashMap<>(original.strategy());
        rows.put("0:foreign", Map.of("fold", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxFrozenContinuationPreflopGame(
                                game,
                                new CfrSolution(1, rows),
                                SixMaxContinuationStudyBudget.standard()));
    }
}
