package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class SixMaxTextureConditionalAuditTest {
    @Test
    void
            conditionalPosteriorUsesActionReachAndPhysicalTextureCountsWithZeroReachSupportRetainedByParent() {
        var source = SixMaxTextureFlopGameTest.source();
        var game = SixMaxTextureFlopGameTest.game(source);
        var rows = new LinkedHashMap<>(source.solution().strategy());
        for (var root : game.sourceGame().chanceOutcomes(game.sourceGame().initialState())) {
            var state = root.state();
            for (var chosen : SixMaxConnectedPreflopGameTest.HISTORY) {
                var row = new LinkedHashMap<String, Double>();
                for (String action : game.sourceGame().legalActions(state))
                    row.put(action, action.equals(chosen.action()) ? 1.0 : 0.0);
                rows.put(
                        game.sourceGame().currentPlayer(state)
                                + ":"
                                + game.sourceGame().informationSet(state),
                        row);
                state = game.sourceGame().afterAction(state, chosen.action());
            }
        }
        var baseline = game.checkdownBaseline(new CfrSolution(1, rows));
        var audit = SixMaxTextureConditionalAudit.assess(game, baseline);
        assertEquals(6, audit.size());
        assertEquals(
                1,
                audit.stream()
                        .mapToDouble(
                                SixMaxTextureConditionalAudit.Conditional
                                        ::textureProbabilityGivenHistory)
                        .sum(),
                1e-15);
        var counts = game.payoffTable().deals();
        for (int t = 0; t < 6; t++) {
            var conditional = audit.get(t);
            assertEquals(1, conditional.historyProbability(), 1e-15);
            assertEquals(
                    (.75 * counts.get(0).flopCounts().get(t)
                                    + .25 * counts.get(1).flopCounts().get(t))
                            / 9880,
                    conditional.textureProbabilityGivenHistory(),
                    1e-15);
            assertEquals(2, conditional.posteriorPrivateDeals());
            assertEquals(1, conditional.firstCombosAtFivePercent());
            assertEquals(1, conditional.secondCombosAtFivePercent());
            assertEquals(0, conditional.quality().nashConvBb(), 1e-12);
            assertEquals(
                    1,
                    conditional.firstMarginal().values().stream()
                            .mapToDouble(Double::doubleValue)
                            .sum(),
                    1e-15);
        }
        var root =
                game.sourceGame()
                        .chanceOutcomes(game.sourceGame().initialState())
                        .getFirst()
                        .state();
        String key = "0:" + game.sourceGame().informationSet(root);
        var exclude = new LinkedHashMap<String, Double>();
        for (String action : game.sourceGame().legalActions(root))
            exclude.put(action, action.equals("call") ? 1.0 : 0.0);
        rows.put(key, exclude);
        var filtered =
                SixMaxTextureConditionalAudit.assess(
                        game, game.checkdownBaseline(new CfrSolution(1, rows)));
        for (int t = 0; t < 6; t++) {
            assertEquals(.25, filtered.get(t).historyProbability(), 1e-15);
            assertEquals(
                    counts.get(1).flopCounts().get(t) / 9880.0,
                    filtered.get(t).textureProbabilityGivenHistory(),
                    1e-15);
            assertEquals(1, filtered.get(t).posteriorPrivateDeals());
        }
        assertEquals(2, game.chanceOutcomes(game.initialState()).size());
        root = game.sourceGame().chanceOutcomes(game.sourceGame().initialState()).getLast().state();
        rows.put("0:" + game.sourceGame().informationSet(root), exclude);
        assertTrue(
                SixMaxTextureConditionalAudit.assess(
                                game, game.checkdownBaseline(new CfrSolution(1, rows)))
                        .isEmpty());
    }

    @Test
    void rejectsIncompleteParentInsteadOfAuditingImplicitUniformDecisions() {
        var source = SixMaxTextureFlopGameTest.source();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxTextureConditionalAudit.assess(
                                SixMaxTextureFlopGameTest.game(source), source.solution()));
    }
}
