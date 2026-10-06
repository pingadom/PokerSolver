package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxConnectedPolicyStabilityTest {
    record Fixture(SixMaxConnectedPreflopGame game, CfrSolution policy) {}

    static Fixture fixture() throws Exception {
        var source = SixMaxConnectedPolicyCheckpointTest.source();
        var snapshot = SixMaxConnectedPolicyCheckpointTest.snapshot(source);
        var game = new SixMaxConnectedPreflopGame(source.rebuildGame(), snapshot.selections());
        var rows = new HashMap<String, Map<String, Double>>();
        snapshot.policy()
                .strategy()
                .forEach(
                        (key, row) ->
                                rows.put(
                                        key,
                                        pure(row, row.containsKey("fold") ? "fold" : "check")));
        return new Fixture(game, new CfrSolution(1, rows));
    }

    private static Map<String, Double> pure(Map<String, Double> row, String selected) {
        assertTrue(row.containsKey(selected));
        var result = new HashMap<String, Double>();
        row.keySet().forEach(action -> result.put(action, action.equals(selected) ? 1.0 : 0.0));
        return Map.copyOf(result);
    }

    static SixMaxConnectedPolicyStability.Stage stage(
            SixMaxConnectedPolicyStability.Report report, String name) {
        return report.stages().stream()
                .filter(stage -> stage.stage().equals(name))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void selfComparisonHasZeroDisagreementAndExplicitlyUnreachedPostflop() throws Exception {
        var fixture = fixture();
        var report =
                SixMaxConnectedPolicyStability.assess(
                        fixture.game(),
                        fixture.policy(),
                        fixture.policy(),
                        SixMaxContinuationStudyBudget.widerFlops());
        var pre = stage(report, "PREFLOP");
        var post = stage(report, "POSTFLOP");
        assertEquals(report.firstSolutionHash(), report.secondSolutionHash());
        assertEquals(0, pre.maximumTotalVariation());
        assertEquals(0, pre.reachWeightedTotalVariation());
        assertEquals(5, pre.firstExpectedDecisionEncounters(), 1e-12);
        assertEquals(5, pre.secondExpectedDecisionEncounters(), 1e-12);
        assertTrue(post.informationSets() > 0);
        assertEquals(0, post.reachedByEitherPolicy());
        assertEquals(0, post.firstExpectedDecisionEncounters());
        assertNull(post.reachWeightedTotalVariation());
    }

    @Test
    void reachWeightedVariationUsesBothPoliciesAndIsSymmetric() throws Exception {
        var fixture = fixture();
        var rows = new HashMap<>(fixture.policy().strategy());
        for (var outcome : fixture.game().chanceOutcomes(fixture.game().initialState())) {
            var key = "0:" + fixture.game().informationSet(outcome.state());
            rows.put(key, pure(rows.get(key), "call"));
        }
        var other = new CfrSolution(1, rows);
        var forward =
                SixMaxConnectedPolicyStability.assess(
                        fixture.game(),
                        fixture.policy(),
                        other,
                        SixMaxContinuationStudyBudget.widerFlops());
        var reverse =
                SixMaxConnectedPolicyStability.assess(
                        fixture.game(),
                        other,
                        fixture.policy(),
                        SixMaxContinuationStudyBudget.widerFlops());
        var pre = stage(forward, "PREFLOP");
        var swapped = stage(reverse, "PREFLOP");
        assertEquals(5, pre.firstExpectedDecisionEncounters(), 1e-12);
        assertEquals(6, pre.secondExpectedDecisionEncounters(), 1e-12);
        assertEquals(1 / 5.5, pre.reachWeightedTotalVariation(), 1e-12);
        assertEquals(1, pre.maximumTotalVariation());
        assertEquals(pre.reachWeightedTotalVariation(), swapped.reachWeightedTotalVariation());
        assertEquals(pre.uniformMeanTotalVariation(), swapped.uniformMeanTotalVariation());
        assertEquals(
                pre.firstExpectedDecisionEncounters(), swapped.secondExpectedDecisionEncounters());
        assertEquals(forward.firstSolutionHash(), reverse.secondSolutionHash());
    }

    @Test
    void unreachableRowDifferencesAreReportedWithoutInventingReachedDisagreement()
            throws Exception {
        var fixture = fixture();
        var rows = new HashMap<>(fixture.policy().strategy());
        var key =
                rows.keySet().stream()
                        .sorted()
                        .filter(
                                k ->
                                        k.contains(":postflop:")
                                                && rows.get(k).containsKey("check")
                                                && rows.get(k).size() > 1)
                        .findFirst()
                        .orElseThrow();
        var action =
                rows.get(key).keySet().stream()
                        .filter(a -> !a.equals("check"))
                        .findFirst()
                        .orElseThrow();
        rows.put(key, pure(rows.get(key), action));
        var report =
                SixMaxConnectedPolicyStability.assess(
                        fixture.game(),
                        fixture.policy(),
                        new CfrSolution(1, rows),
                        SixMaxContinuationStudyBudget.widerFlops());
        var post = stage(report, "POSTFLOP");
        assertEquals(1, post.maximumTotalVariation());
        assertEquals(key, post.maximumDifferenceInformationSet());
        assertEquals(1.0 / post.informationSets(), post.uniformMeanTotalVariation(), 1e-12);
        assertNull(post.reachWeightedTotalVariation());
        assertEquals(0, post.reachedByEitherPolicy());
        assertEquals(0, stage(report, "PREFLOP").reachWeightedTotalVariation());
    }

    @Test
    void rarePostflopDisagreementIsNormalizedWithinItsOwnStage() throws Exception {
        var fixture = fixture();
        var firstRows = new HashMap<>(fixture.policy().strategy());
        var secondRows = new HashMap<>(fixture.policy().strategy());
        var selection = fixture.game().selections().getFirst();
        for (var root : fixture.game().chanceOutcomes(fixture.game().initialState())) {
            var state = root.state();
            for (var move : selection.history()) {
                var key =
                        fixture.game().currentPlayer(state)
                                + ":"
                                + fixture.game().informationSet(state);
                secondRows.put(key, pure(secondRows.get(key), move.action()));
                state = fixture.game().afterAction(state, move.action());
            }
            for (var flop : fixture.game().chanceOutcomes(state)) {
                if (flop.state().otherFlopsCheckdown()) continue;
                var key =
                        fixture.game().currentPlayer(flop.state())
                                + ":"
                                + fixture.game().informationSet(flop.state());
                var action =
                        firstRows.get(key).keySet().stream()
                                .filter(a -> !a.equals("check"))
                                .findFirst()
                                .orElseThrow();
                firstRows.put(key, pure(firstRows.get(key), action));
            }
        }
        var report =
                SixMaxConnectedPolicyStability.assess(
                        fixture.game(),
                        new CfrSolution(1, firstRows),
                        new CfrSolution(1, secondRows),
                        SixMaxContinuationStudyBudget.widerFlops());
        var post = stage(report, "POSTFLOP");
        assertEquals(0, post.firstExpectedDecisionEncounters());
        assertTrue(post.secondExpectedDecisionEncounters() > 0);
        assertTrue(post.secondExpectedDecisionEncounters() < .001);
        assertEquals(1.0 / 6, post.reachWeightedTotalVariation(), 1e-12);
        assertEquals(1, post.maximumTotalVariation());
    }

    @Test
    void refusesIncompleteForeignOrIllegalPoliciesAndExceededBudget() throws Exception {
        var fixture = fixture();
        var rows = new HashMap<>(fixture.policy().strategy());
        rows.remove(rows.keySet().iterator().next());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConnectedPolicyStability.assess(
                                fixture.game(),
                                fixture.policy(),
                                new CfrSolution(1, rows),
                                SixMaxContinuationStudyBudget.widerFlops()));
        var foreign = new HashMap<>(fixture.policy().strategy());
        foreign.put("0:foreign", Map.of("check", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConnectedPolicyStability.assess(
                                fixture.game(),
                                new CfrSolution(1, foreign),
                                fixture.policy(),
                                SixMaxContinuationStudyBudget.widerFlops()));
        var illegal = new HashMap<>(fixture.policy().strategy());
        illegal.put(illegal.keySet().iterator().next(), Map.of("teleport", 1.0));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxConnectedPolicyStability.assess(
                                fixture.game(),
                                fixture.policy(),
                                new CfrSolution(1, illegal),
                                SixMaxContinuationStudyBudget.widerFlops()));
        assertThrows(
                SixMaxContinuationStudyBudget.Exceeded.class,
                () ->
                        SixMaxConnectedPolicyStability.assess(
                                fixture.game(),
                                fixture.policy(),
                                fixture.policy(),
                                new SixMaxContinuationStudyBudget(16, 1)));
    }
}
