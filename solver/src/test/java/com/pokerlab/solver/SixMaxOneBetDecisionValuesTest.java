package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.State;
import java.util.*;
import org.junit.jupiter.api.Test;

class SixMaxOneBetDecisionValuesTest {
    static List<ChanceOutcome<State>> roots(SixMaxSuitConditionalRefinementTest.Fixture f) {
        var transition =
                new SixMaxPolicyFlopTransition(
                        f.game().sourceGame(),
                        SixMaxPreflopContinuationFeedback.preflopPolicy(f.checkpoint().solution()),
                        f.game().selections().getFirst().history());
        for (int observation = 0; observation < f.table().observations().size(); observation++) {
            var posterior =
                    SixMaxFlopConditionalDiagnostics.posterior(
                            f.game().core(), transition, observation);
            if (posterior.roots().size() == 2) return posterior.roots();
        }
        throw new AssertionError("Two-world posterior required");
    }

    @Test
    void analyticPotAccountingIncludesOptimalFutureCallAndSeparatesContinuationRegret()
            throws Exception {
        var f = SixMaxSuitConditionalRefinementTest.fixture();
        var roots = roots(f);
        var rows = new LinkedHashMap<>(f.checkpoint().solution().strategy());
        var first = roots.getFirst().state();
        var second = f.game().afterAction(first, "k");
        rows.put(
                f.game().currentPlayer(second) + ":" + f.game().informationSet(second),
                Map.of("k", 0.0, "b", 1.0));
        var policy = new CfrSolution(1, rows);
        var decisions = SixMaxOneBetDecisionValues.assess(f.game().core(), roots, policy);
        var root =
                decisions.stream()
                        .filter(d -> d.row().actions().isEmpty())
                        .findFirst()
                        .orElseThrow();
        double pot = f.game().sourceGame().publicBettingState(first.preflop()).potBb();
        assertEquals(6.5, pot);
        assertEquals(pot, root.row().values().actionEvBb().get("b"), 1e-12);
        assertEquals(pot / 2, root.row().values().actionEvBb().get("k"), 1e-12);
        assertEquals(0, root.row().values().profileEvBb(), 1e-12);
        assertEquals(pot, root.row().values().decisionRegretBb(), 1e-12);
        assertEquals(pot / 2, root.row().values().rootMixtureRegretBb(), 1e-12);
        assertEquals(pot / 2, root.row().values().continuationRegretBb(), 1e-12);
        var facing =
                decisions.stream()
                        .filter(d -> d.row().actions().equals("kb"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(0, facing.row().values().actionEvBb().get("f"), 1e-12);
        assertEquals(pot / 2, facing.row().values().actionEvBb().get("c"), 1e-12);
        var offPath =
                decisions.stream()
                        .filter(d -> d.row().actions().equals("b"))
                        .findFirst()
                        .orElseThrow();
        assertEquals("ZERO_POLICY_REACH", offPath.row().status());
        assertNull(offPath.row().values());
        assertTrue(offPath.roots().isEmpty());
    }

    @Test
    void publicActionLikelihoodAndActualOwnCardsConditionFullJointWorlds() throws Exception {
        var f = SixMaxSuitConditionalRefinementTest.fixture();
        var history =
                List.of(
                        new PublicAction(Seat.UTG, "raise:3.0"),
                        new PublicAction(Seat.HJ, "fold"),
                        new PublicAction(Seat.CO, "fold"),
                        new PublicAction(Seat.BTN, "fold"),
                        new PublicAction(Seat.SB, "fold"),
                        new PublicAction(Seat.BB, "call"));
        var game =
                new SixMaxOneBetFlopGame(
                        f.source(),
                        SixMaxSuitRefinementPayoffTable.view(f.table()),
                        List.of(new SixMaxRankTextureFlopGame.Selection(history, .5)));
        int observation = 0;
        while (f.table().deals().get(0).flopCounts().get(observation) == 0
                || f.table().deals().get(1).flopCounts().get(observation) == 0) observation++;
        var roots = new ArrayList<ChanceOutcome<State>>();
        for (var world : game.sourceGame().chanceOutcomes(game.sourceGame().initialState())) {
            var state = world.state();
            for (var action : history)
                state = game.sourceGame().afterAction(state, action.action());
            roots.add(new ChanceOutcome<>(new State(state, observation, ""), world.probability()));
        }
        assertEquals(.75, roots.getFirst().probability());
        var baseline = game.checkdownBaseline(f.source().solution());
        var rows = new LinkedHashMap<>(baseline.strategy());
        for (var root : roots) {
            var state = game.afterAction(root.state(), "k");
            double bet = state.preflop().dealIndex() == 0 ? .2 : .8;
            rows.put(
                    game.currentPlayer(state) + ":" + game.informationSet(state),
                    Map.of("b", bet, "k", 1 - bet));
        }
        var decisions = SixMaxOneBetDecisionValues.assess(game, roots, new CfrSolution(1, rows));
        var facing =
                decisions.stream()
                        .filter(d -> d.row().actions().equals("kb"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(Seat.BB, facing.row().actor());
        assertEquals(.35, facing.row().prefixProbability(), 1e-12);
        assertEquals(2, facing.row().posteriorJointDeals());
        assertEquals(3.0 / 7, facing.roots().getFirst().probability(), 1e-12);
        assertEquals(4.0 / 7, facing.roots().getLast().probability(), 1e-12);
        assertEquals(
                .75 - 3.0 / 7,
                SixMaxOneBetDecisionValues.posteriorDistance(roots, facing.roots()),
                1e-12);
        var own = decisions.stream().filter(d -> d.row().actions().equals("k")).toList();
        assertEquals(2, own.size());
        assertTrue(own.stream().allMatch(d -> d.roots().size() == 1));
        assertEquals(
                Set.of(.25, .75),
                new HashSet<>(
                        own.stream().map(d -> d.row().ownHandProbabilityGivenPrefix()).toList()));
    }

    @Test
    void invalidPosteriorsAndMissingPolicyRowsFailInsteadOfFabricatingValues() throws Exception {
        var f = SixMaxSuitConditionalRefinementTest.fixture();
        var roots = roots(f);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxOneBetDecisionValues.assess(
                                f.game().core(),
                                List.of(roots.getFirst(), roots.getFirst()),
                                f.checkpoint().solution()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxOneBetDecisionValues.assess(
                                f.game().core(),
                                List.of(
                                        new ChanceOutcome<>(
                                                roots.getFirst().state(), Double.MIN_VALUE)),
                                f.checkpoint().solution()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxOneBetDecisionValues.assess(
                                f.game().core(), roots, new CfrSolution(1, Map.of())));
        var rows = new LinkedHashMap<>(f.checkpoint().solution().strategy());
        var state = roots.getFirst().state();
        rows.put(
                f.game().currentPlayer(state) + ":" + f.game().informationSet(state),
                Map.of("k", Double.MIN_VALUE, "b", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxOneBetDecisionValues.assess(
                                f.game().core(), roots, new CfrSolution(1, rows)));
    }
}
