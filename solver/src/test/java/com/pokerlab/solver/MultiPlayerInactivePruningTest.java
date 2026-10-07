package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.OptionalDouble;
import org.junit.jupiter.api.Test;

class MultiPlayerInactivePruningTest {
    record State(int hidden, String actions, int outcome) {}

    /** A dropped seat keeps -2 regardless of later choices or rare runouts. */
    static class FoldGame implements MultiPlayerCfrGame<State> {
        final int players;

        FoldGame(int players) {
            this.players = players;
        }

        public int playerCount() {
            return players;
        }

        public State initialState() {
            return new State(-1, "", -1);
        }

        public boolean isTerminal(State s) {
            return s.outcome() >= 0;
        }

        public int currentPlayer(State s) {
            return s.hidden() < 0 || s.actions().length() == players ? -1 : s.actions().length();
        }

        public List<String> legalActions(State s) {
            return List.of("d", "s");
        }

        public String informationSet(State s) {
            return s.hidden() + ":" + s.actions();
        }

        public State afterAction(State s, String action) {
            return new State(s.hidden(), s.actions() + action, -1);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
            if (s.hidden() < 0)
                return List.of(
                        new ChanceOutcome<>(new State(0, "", -1), .1),
                        new ChanceOutcome<>(new State(1, "", -1), .9));
            return List.of(
                    new ChanceOutcome<>(new State(s.hidden(), s.actions(), 0), .999),
                    new ChanceOutcome<>(new State(s.hidden(), s.actions(), 1), .001));
        }

        public double[] terminalUtilities(State s) {
            double[] values = new double[players];
            for (int p = 0; p < players; p++)
                values[p] =
                        s.actions().charAt(p) == 'd'
                                ? -2
                                : 1
                                        + .1 * s.actions().chars().filter(c -> c == 's').count()
                                        + s.hidden()
                                        + 100 * s.outcome();
            return values;
        }

        public OptionalDouble inactivePlayerUtility(State s, int p) {
            return s.actions().length() > p && s.actions().charAt(p) == 'd'
                    ? OptionalDouble.of(-2)
                    : OptionalDouble.empty();
        }

        public double chanceBaselineUtility(State s, int p) {
            return .25;
        }
    }

    static void samePolicy(CfrSolution original, CfrSolution pruned, double tolerance) {
        assertEquals(original.iterations(), pruned.iterations());
        assertEquals(original.strategy().keySet(), pruned.strategy().keySet());
        original.strategy()
                .forEach(
                        (key, row) -> {
                            assertEquals(row.keySet(), pruned.strategy().get(key).keySet());
                            row.forEach(
                                    (action, value) ->
                                            assertEquals(
                                                    value,
                                                    pruned.strategy().get(key).get(action),
                                                    tolerance,
                                                    key + ":" + action));
                        });
    }

    @Test
    void exhaustivePruningPreservesEveryPlayersRowsRegretsAndAveraging() {
        for (int players = 2; players <= 6; players++)
            for (var variant : CfrSolver.Variant.values())
                for (boolean linear : List.of(false, true)) {
                    if (linear && variant != CfrSolver.Variant.VANILLA) continue;
                    var game = new FoldGame(players);
                    var original =
                            new MultiPlayerCfrSolver<>(
                                    game,
                                    variant,
                                    MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                                    0,
                                    0,
                                    true,
                                    linear);
                    var pruned =
                            new MultiPlayerCfrSolver<>(
                                    game,
                                    variant,
                                    MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE,
                                    0,
                                    0,
                                    true,
                                    linear,
                                    MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
                    var reference = original.solve(25);
                    var candidate = pruned.solve(25);
                    samePolicy(reference, candidate, 1e-12);
                    assertEquals(
                            0,
                            MultiPlayerStrategyCompletion.uniformAtUnseen(game, candidate, 10000)
                                    .addedInformationSets());
                    assertTrue(pruned.inactiveUtilityPrunedNodes() > 0);
                    assertTrue(
                            pruned.statistics().visitedNodes()
                                    < original.statistics().visitedNodes());
                    var count = pruned.inactiveUtilityPrunedNodes();
                    var statistics = pruned.statistics();
                    assertEquals(candidate, pruned.solve(25));
                    assertEquals(count, pruned.inactiveUtilityPrunedNodes());
                    assertEquals(statistics, pruned.statistics());
                }
    }

    @Test
    void sampledPruningRetainsProposalAndBaselineCorrectionAndLearnsDominantAction() {
        var game = new FoldGame(3);
        var reference = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.VANILLA).solve(2000);
        for (var mode : MultiPlayerCfrSolver.ChanceMode.values()) {
            if (mode == MultiPlayerCfrSolver.ChanceMode.EXHAUSTIVE) continue;
            for (boolean baseline : List.of(false, true))
                for (long seed : List.of(11L, 711L, 901L)) {
                    var solver =
                            new MultiPlayerCfrSolver<>(
                                    game,
                                    CfrSolver.Variant.VANILLA,
                                    mode,
                                    seed,
                                    .5,
                                    baseline,
                                    false,
                                    MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
                    var solution = solver.solve(2000);
                    assertEquals(reference.strategy().keySet(), solution.strategy().keySet());
                    // Off-path rows can differ under sampling when their counterfactual reach
                    // vanishes. Compare reached rows and independently evaluate deviations.
                    for (int hidden = 0; hidden < 2; hidden++)
                        for (int player = 0; player < 3; player++)
                            assertTrue(
                                    solution.at(player, hidden + ":" + "s".repeat(player)).get("s")
                                            > .995);
                    assertTrue(solver.inactiveUtilityPrunedNodes() > 0);
                    // This fixture has only two chance depths; RUNOUTS enumerates both.
                    boolean sampled = mode != MultiPlayerCfrSolver.ChanceMode.SAMPLED_RUNOUTS;
                    assertEquals(sampled, solver.statistics().sampledChanceNodes() > 0);
                    assertEquals(
                            sampled && baseline, solver.statistics().baselineCorrections() > 0);
                    assertTrue(
                            MultiPlayerInformationSetBestResponse.assess(game, solution)
                                            .nashConvBb()
                                    < .01);
                }
        }
    }

    @Test
    void fixedSuffixStillGetsItsAncestorsImportanceAndBaselineCorrection() {
        var game =
                new FoldGame(2) {
                    @Override
                    public List<ChanceOutcome<State>> chanceOutcomes(State s) {
                        if (s.hidden() < 0)
                            return List.of(
                                    new ChanceOutcome<>(new State(0, "", -1), .999),
                                    new ChanceOutcome<>(new State(1, "", -1), .001));
                        return super.chanceOutcomes(s);
                    }

                    @Override
                    public String informationSet(State s) {
                        return "risk:" + s.actions();
                    }

                    @Override
                    public double[] terminalUtilities(State s) {
                        return new double[] {
                            s.actions().charAt(0) == 'd' ? 1 : 100 * s.hidden(),
                            s.actions().charAt(1) == 's' ? 1 : -1
                        };
                    }

                    @Override
                    public OptionalDouble inactivePlayerUtility(State s, int p) {
                        if (s.actions().length() <= p) return OptionalDouble.empty();
                        return OptionalDouble.of(
                                p == 0
                                        ? s.actions().charAt(0) == 'd' ? 1 : 100 * s.hidden()
                                        : s.actions().charAt(1) == 's' ? 1 : -1);
                    }
                };
        for (boolean baseline : List.of(false, true))
            for (long seed : List.of(11L, 711L, 901L)) {
                var solver =
                        new MultiPlayerCfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                MultiPlayerCfrSolver.ChanceMode.SAMPLED,
                                seed,
                                .5,
                                baseline,
                                false,
                                MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
                var policy = solver.solve(4000);
                assertTrue(policy.at(0, "risk:").get("d") > .99);
                assertTrue(
                        MultiPlayerInformationSetBestResponse.assess(game, policy).nashConvBb()
                                < .01);
                assertEquals(baseline, solver.statistics().baselineCorrections() > 0);
            }
    }

    @Test
    void defaultsNeverConsultTheHookAndNonfiniteGuaranteesAreRejected() {
        var throwing =
                new FoldGame(2) {
                    @Override
                    public OptionalDouble inactivePlayerUtility(State s, int p) {
                        throw new AssertionError("Default must not consult shortcut");
                    }
                };
        var legacy = new MultiPlayerCfrSolver<>(throwing, CfrSolver.Variant.CFR_PLUS);
        var explicit =
                new MultiPlayerCfrSolver<>(
                        throwing,
                        CfrSolver.Variant.CFR_PLUS,
                        MultiPlayerCfrSolver.InactivePruning.NONE);
        assertEquals(legacy.solve(10), explicit.solve(10));
        assertEquals(legacy.statistics(), explicit.statistics());
        assertEquals(0, explicit.inactiveUtilityPrunedNodes());
        for (double bad : List.of(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            var invalid =
                    new FoldGame(2) {
                        @Override
                        public OptionalDouble inactivePlayerUtility(State s, int p) {
                            return OptionalDouble.of(bad);
                        }
                    };
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new MultiPlayerCfrSolver<>(
                                            invalid,
                                            CfrSolver.Variant.CFR_PLUS,
                                            MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY)
                                    .solve(1));
        }
        assertThrows(
                NullPointerException.class,
                () ->
                        new MultiPlayerCfrSolver<>(
                                new FoldGame(2),
                                CfrSolver.Variant.CFR_PLUS,
                                (MultiPlayerCfrSolver.InactivePruning) null));
    }

    @Test
    void emptyHookChangesNothing() {
        var game =
                new FoldGame(3) {
                    @Override
                    public OptionalDouble inactivePlayerUtility(State s, int p) {
                        return OptionalDouble.empty();
                    }
                };
        var original = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS);
        var pruned =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.CFR_PLUS,
                        MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
        assertEquals(original.solve(15), pruned.solve(15));
        assertEquals(original.statistics(), pruned.statistics());
        assertEquals(0, pruned.inactiveUtilityPrunedNodes());
    }
}
