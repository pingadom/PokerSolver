package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxConditionalPostflopRefinementTest {
    static CfrSolution uniform(SixMaxPreflopCheckdownGame base) {
        return MultiPlayerStrategyCompletion.uniformAtUnseen(
                        base, new CfrSolution(1, Map.of()), 100_000)
                .solution();
    }

    @Test
    void improvesConditionalPlayAndIndependentlyRechecksTheParent() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var original = SixMaxConnectedPreflopAudit.liftCheckdown(game, uniform(game.source()));
        var originalHash = SixMaxConnectedPostflopAudit.solutionHash(original);
        var result = SixMaxConditionalPostflopRefinement.refine(game, original, 30, b -> {});
        var branch = result.report().branches().getFirst();
        assertEquals("REFINED", branch.status());
        assertEquals(2, branch.posteriorJointDeals());
        assertEquals(1.0 / 9880, branch.flopProbabilityGivenHistory(), 1e-15);
        assertTrue(branch.after().gap() < branch.before().gap() / 20);
        assertTrue(result.report().replacedInformationSets() > 0);
        assertEquals(original.strategy().keySet(), result.candidate().strategy().keySet());
        for (var row : original.strategy().entrySet())
            if (!row.getKey().contains(":postflop:"))
                assertEquals(row.getValue(), result.candidate().strategy().get(row.getKey()));
        assertEquals(originalHash, SixMaxConnectedPostflopAudit.solutionHash(original));
        assertEquals(originalHash, result.report().originalSolutionHash());
        assertNotEquals(originalHash, result.report().candidateSolutionHash());
        assertEquals(
                0,
                MultiPlayerStrategyCompletion.uniformAtUnseen(game, result.candidate(), 2_000_000)
                        .addedInformationSets());
        var parent = MultiPlayerInformationSetBestResponse.assess(game, result.candidate());
        assertEquals(parent.nashConvBb(), result.report().candidateQuality().nashConvBb(), 1e-12);
        assertEquals(6, result.report().candidateQuality().deviationGainsBb().size());
        assertEquals(
                SixMaxConnectedPreflopAudit.bettingProbability(game, original),
                result.report().bettingContinuationProbability(),
                1e-15);
        assertEquals(
                branch.after().gap(),
                SixMaxConnectedPreflopAudit.conditionalPostflop(game, result.candidate())
                        .getFirst()
                        .bestResponse()
                        .gap(),
                1e-12);
    }

    @Test
    void preservesCounterfactualRowsOutsideTheFrozenPosteriorAndSkipsBlockedFlops() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var pre = new LinkedHashMap<>(uniform(base).strategy());
        for (var root : base.chanceOutcomes(base.initialState())) {
            var state = root.state();
            var weights = new LinkedHashMap<String, Double>();
            String chosen = state.dealIndex() == 0 ? "fold" : "call";
            for (String action : base.legalActions(state))
                weights.put(action, action.equals(chosen) ? 1.0 : 0.0);
            pre.put(base.currentPlayer(state) + ":" + base.informationSet(state), weights);
        }
        var policy = new CfrSolution(1, pre);
        var game =
                new SixMaxConnectedPreflopGame(
                        base, List.of(SixMaxConnectedPreflopGameTest.selection("2d 3d 4d")));
        var original = SixMaxConnectedPreflopAudit.liftCheckdown(game, policy);
        var result = SixMaxConditionalPostflopRefinement.refine(game, original, 1, b -> {});
        assertEquals(1, result.report().branches().getFirst().posteriorJointDeals());
        var unsupported =
                original.strategy().entrySet().stream()
                        .filter(
                                row ->
                                        row.getKey().contains("|T:As:")
                                                || row.getKey().contains("|R:As:"))
                        .toList();
        assertFalse(unsupported.isEmpty());
        for (var row : unsupported)
            assertEquals(row.getValue(), result.candidate().strategy().get(row.getKey()));
        var blocked =
                new SixMaxConnectedPreflopGame(
                        base, List.of(SixMaxConnectedPreflopGameTest.selection("As 2d 3d")));
        var blockedPolicy = SixMaxConnectedPreflopAudit.liftCheckdown(blocked, policy);
        var skipped =
                SixMaxConditionalPostflopRefinement.refine(blocked, blockedPolicy, 1, b -> {});
        assertEquals("ZERO_FLOP_REACH", skipped.report().branches().getFirst().status());
        assertEquals(blockedPolicy, skipped.candidate());
    }

    @Test
    void branchLabelsMatchSelectionsWhenCoverageHasADifferentSortOrder() {
        var base = SixMaxConnectedPreflopGameTest.base();
        var otherHistory =
                List.of(
                        new PublicAction(Seat.UTG, "call"),
                        new PublicAction(Seat.HJ, "fold"),
                        new PublicAction(Seat.CO, "fold"),
                        new PublicAction(Seat.BTN, "fold"),
                        new PublicAction(Seat.SB, "fold"),
                        new PublicAction(Seat.BB, "check"));
        var other =
                new SixMaxConnectedPreflopGame.Selection(
                        otherHistory,
                        List.of(List.of(Card.parse("2d"), Card.parse("3d"), Card.parse("4d"))),
                        1.25,
                        2.5,
                        5);
        var pre = new LinkedHashMap<>(uniform(base).strategy());
        for (var root : base.chanceOutcomes(base.initialState())) {
            var state = root.state();
            var weights = new LinkedHashMap<String, Double>();
            for (String action : base.legalActions(state))
                weights.put(action, action.equals("raise:100.0") ? 1.0 : 0.0);
            pre.put(base.currentPlayer(state) + ":" + base.informationSet(state), weights);
        }
        for (var selections :
                List.of(
                        List.of(SixMaxConnectedPreflopGameTest.selection("2d 3d 4d"), other),
                        List.of(other, SixMaxConnectedPreflopGameTest.selection("2d 3d 4d")))) {
            var game = new SixMaxConnectedPreflopGame(base, selections);
            var original = SixMaxConnectedPreflopAudit.liftCheckdown(game, new CfrSolution(1, pre));
            var result = SixMaxConditionalPostflopRefinement.refine(game, original, 1, b -> {});
            assertEquals(original, result.candidate());
            for (int i = 0; i < selections.size(); i++) {
                var selection = selections.get(i);
                var expected =
                        game.coverage().stream()
                                .filter(c -> c.actions().equals(selection.history()))
                                .findFirst()
                                .orElseThrow()
                                .publicHistory();
                assertEquals(expected, result.report().branches().get(i).publicHistory());
            }
        }
    }

    @Test
    void leavesZeroReachHistoriesUntouchedAndRejectsImplicitCompletion() {
        var game = SixMaxConnectedPreflopGameTest.game();
        var pre = new LinkedHashMap<>(uniform(game.source()).strategy());
        for (var root : game.source().chanceOutcomes(game.source().initialState())) {
            var state = root.state();
            var weights = new LinkedHashMap<String, Double>();
            for (String action : game.source().legalActions(state))
                weights.put(action, action.equals("call") ? 1.0 : 0.0);
            pre.put(
                    game.source().currentPlayer(state) + ":" + game.source().informationSet(state),
                    weights);
        }
        var original = SixMaxConnectedPreflopAudit.liftCheckdown(game, new CfrSolution(1, pre));
        var result = SixMaxConditionalPostflopRefinement.refine(game, original, 1, b -> {});
        assertEquals(original, result.candidate());
        assertEquals("ZERO_HISTORY_REACH", result.report().branches().getFirst().status());
        assertNull(result.report().branches().getFirst().after());
        assertEquals(0, result.report().replacedInformationSets());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConditionalPostflopRefinement.refine(
                                game, new CfrSolution(1, Map.of()), 1, b -> {}));
        for (int budget : List.of(0, 501))
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SixMaxConditionalPostflopRefinement.refine(
                                    game, original, budget, b -> {}));
    }
}
