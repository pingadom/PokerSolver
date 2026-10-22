package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CachedTwoPlayerCfrGameTest {
    private static FiniteTwoPlayerSequenceForm.Node unary(
            int actor, String key, FiniteTwoPlayerSequenceForm.Node child) {
        return new FiniteTwoPlayerSequenceForm.Node(
                actor, actor + ":" + key, List.of("a"), List.of(), List.of(child), List.of());
    }

    @Test
    void noCacheIsCreatedForAThirdDecisionMakerOrAnOverBudgetTree() {
        var leaf =
                new FiniteTwoPlayerSequenceForm.Node(
                        -2, "", List.of(), List.of(), List.of(), List.of(0., 0., 0., 0., 0., 0.));
        var threeActors = unary(0, "first", unary(1, "second", unary(2, "third", leaf)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CachedTwoPlayerCfrGame(
                                new FiniteTwoPlayerSequenceForm.Snapshot(6, threeActors)));
        var deep = leaf;
        for (int depth = 0; depth <= FiniteTwoPlayerSequenceForm.MAX_DEPTH; depth++)
            deep = unary(depth % 2, "depth" + depth, deep);
        var overBudget = new FiniteTwoPlayerSequenceForm.Snapshot(6, deep);
        assertThrows(IllegalArgumentException.class, () -> new CachedTwoPlayerCfrGame(overBudget));
    }

    private static class Hidden extends FiniteTwoPlayerSequenceFormTest.Hidden {
        boolean closed;

        Hidden() {
            super(2);
        }

        public int playerCount() {
            return 6;
        }

        public boolean isTerminal(FiniteTwoPlayerSequenceFormTest.State state) {
            if (closed) throw new AssertionError("Source called after cache construction");
            return super.isTerminal(state);
        }

        public double[] terminalUtilities(FiniteTwoPlayerSequenceFormTest.State state) {
            double value = super.terminalUtilities(state)[0];
            return new double[] {value, -value, -0.0, .5, -1, .5};
        }
    }

    private static <S> int sameTree(
            MultiPlayerCfrGame<S> source,
            S state,
            CachedTwoPlayerCfrGame cache,
            CachedTwoPlayerCfrGame.Node copy) {
        assertEquals(source.isTerminal(state), cache.isTerminal(copy));
        if (source.isTerminal(state)) {
            var a = source.terminalUtilities(state);
            var b = cache.terminalUtilities(copy);
            for (int i = 0; i < a.length; i++)
                assertEquals(Double.doubleToRawLongBits(a[i]), Double.doubleToRawLongBits(b[i]));
            return 1;
        }
        assertEquals(source.currentPlayer(state), cache.currentPlayer(copy));
        int count = 1;
        if (source.currentPlayer(state) == -1) {
            var a = source.chanceOutcomes(state);
            var b = cache.chanceOutcomes(copy);
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(
                        Double.doubleToRawLongBits(a.get(i).probability()),
                        Double.doubleToRawLongBits(b.get(i).probability()));
                count += sameTree(source, a.get(i).state(), cache, b.get(i).state());
            }
        } else {
            assertEquals(source.informationSet(state), cache.informationSet(copy));
            assertEquals(source.legalActions(state), cache.legalActions(copy));
            for (String action : source.legalActions(state))
                count +=
                        sameTree(
                                source,
                                source.afterAction(state, action),
                                cache,
                                cache.afterAction(copy, action));
        }
        return count;
    }

    @Test
    void preservesEveryNodeChanceBitActionOrderAndColonInInformationKeys() {
        var source = new Hidden();
        var cache = new CachedTwoPlayerCfrGame(source);
        assertEquals(
                cache.validatedTreeNodes(),
                sameTree(source, source.initialState(), cache, cache.initialState()));
        var decision = cache.chanceOutcomes(cache.initialState()).getFirst().state();
        assertEquals("hidden:type:0", cache.informationSet(decision));
        assertEquals(
                Double.doubleToRawLongBits(-0.0),
                Double.doubleToRawLongBits(cache.inactiveUtilities().get(2)));
    }

    @Test
    void sourceIsDetachedAndSharedCacheNeverSharesRegretTables() {
        var source = new Hidden();
        var cache = new CachedTwoPlayerCfrGame(source);
        source.closed = true;
        var first = new MultiPlayerCfrSolver<>(cache, CfrSolver.Variant.CFR_PLUS);
        var expected = first.solve(73);
        var second = new MultiPlayerCfrSolver<>(cache, CfrSolver.Variant.CFR_PLUS);
        assertEquals(expected, second.solve(73));
        assertEquals(first.statistics(), second.statistics());
        assertEquals(0, second.inactiveUtilityPrunedNodes());
    }

    @Test
    void terminalArraysAndAllReturnedCollectionsCannotChangeTheCache() {
        var cache = new CachedTwoPlayerCfrGame(new Hidden());
        var chance = cache.initialState();
        var decision = cache.chanceOutcomes(chance).getFirst().state();
        var next = cache.afterAction(decision, "a");
        var terminal = cache.afterAction(next, "b");
        var expected = cache.terminalUtilities(terminal);
        cache.terminalUtilities(terminal)[2] = 12345;
        assertArrayEquals(expected, cache.terminalUtilities(terminal));
        assertThrows(
                UnsupportedOperationException.class, () -> cache.chanceOutcomes(chance).clear());
        assertThrows(
                UnsupportedOperationException.class, () -> cache.legalActions(decision).clear());
        assertThrows(
                UnsupportedOperationException.class, () -> cache.inactiveUtilities().put(2, 4.));
    }

    @Test
    void everyStateOperationRejectsOtherOwnersAndNullEvenForInactiveSeats() {
        var cache = new CachedTwoPlayerCfrGame(new Hidden());
        var other = new CachedTwoPlayerCfrGame(new Hidden());
        List<Consumer<CachedTwoPlayerCfrGame.Node>> operations =
                List.of(
                        cache::isTerminal,
                        cache::terminalUtilities,
                        cache::currentPlayer,
                        cache::legalActions,
                        cache::informationSet,
                        cache::chanceOutcomes,
                        state -> cache.afterAction(state, "a"),
                        state -> cache.inactivePlayerUtility(state, 2),
                        state -> cache.inactivePlayerUtility(state, 0));
        for (var operation : operations) {
            assertThrows(
                    IllegalArgumentException.class, () -> operation.accept(other.initialState()));
            assertThrows(IllegalArgumentException.class, () -> operation.accept(null));
        }
    }

    @Test
    void rejectsWrongNodeKindsIllegalActionsAndOutOfRangePlayers() {
        var cache = new CachedTwoPlayerCfrGame(new Hidden());
        var chance = cache.initialState();
        var decision = cache.chanceOutcomes(chance).getFirst().state();
        var terminal = cache.afterAction(cache.afterAction(decision, "a"), "a");
        for (var state : List.of(chance, terminal)) {
            assertThrows(IllegalArgumentException.class, () -> cache.legalActions(state));
            assertThrows(IllegalArgumentException.class, () -> cache.informationSet(state));
            assertThrows(IllegalArgumentException.class, () -> cache.afterAction(state, "a"));
        }
        assertThrows(IllegalArgumentException.class, () -> cache.terminalUtilities(decision));
        assertThrows(IllegalArgumentException.class, () -> cache.terminalUtilities(chance));
        assertThrows(IllegalArgumentException.class, () -> cache.chanceOutcomes(decision));
        assertThrows(IllegalArgumentException.class, () -> cache.chanceOutcomes(terminal));
        assertThrows(IllegalArgumentException.class, () -> cache.afterAction(decision, "foreign"));
        assertThrows(IllegalArgumentException.class, () -> cache.afterAction(decision, null));
        for (int player : List.of(-1, 6))
            assertThrows(
                    IllegalArgumentException.class,
                    () -> cache.inactivePlayerUtility(chance, player));
        assertTrue(cache.inactivePlayerUtility(decision, 0).isEmpty());
        assertEquals(.5, cache.inactivePlayerUtility(decision, 3).orElseThrow());
    }

    @Test
    void ordinaryAndCachedPhysicalGamesHaveIdenticalPoliciesAndWorkInBothExistingModes()
            throws Exception {
        for (String filename :
                List.of(
                        "supplied-four-button-hand-btn-flop-feedback-request.json",
                        "supplied-paired-btn-facing-bet-feedback-request.json",
                        "supplied-weak-hand-qualified-flop-request.json")) {
            var connected =
                    filename.equals("supplied-four-button-hand-btn-flop-feedback-request.json")
                            ? SixMaxBtnFlopFeedback.readRequest(Path.of("../docs/data", filename))
                                    .connected()
                            : SixMaxRefinedFlopFeedback.readRequest(
                                            Path.of("../docs/data", filename))
                                    .conditional()
                                    .parent()
                                    .connected();
            var source =
                    new SixMaxSuppliedFlopBettingGame(
                            connected.input(), connected.flop(), connected.betPotFraction());
            var ordinaryPruned = new SixMaxOwnedFlopReferences(source);
            var cache = new CachedTwoPlayerCfrGame(source);
            for (var mode : MultiPlayerCfrSolver.InactivePruning.values()) {
                var ordinary =
                        new MultiPlayerCfrSolver<>(
                                ordinaryPruned, CfrSolver.Variant.CFR_PLUS, mode);
                var cached = new MultiPlayerCfrSolver<>(cache, CfrSolver.Variant.CFR_PLUS, mode);
                assertEquals(ordinary.solve(80), cached.solve(80), filename + mode);
                assertEquals(ordinary.statistics(), cached.statistics(), filename + mode);
                assertEquals(
                        ordinary.inactiveUtilityPrunedNodes(), cached.inactiveUtilityPrunedNodes());
                assertEquals(
                        mode == MultiPlayerCfrSolver.InactivePruning.NONE
                                ? 6L * cache.validatedTreeNodes() * 80
                                : (2L * cache.validatedTreeNodes() + 4) * 80,
                        cached.statistics().visitedNodes());
            }
        }
    }

    @Test
    void inactiveProofRejectsEvenSubToleranceAndSignedZeroDifferences() {
        for (double changed : List.of(1e-16, 0.0)) {
            var source =
                    new Hidden() {
                        public double[] terminalUtilities(
                                FiniteTwoPlayerSequenceFormTest.State state) {
                            var values = super.terminalUtilities(state);
                            if (state.firstAction() == 1) values[2] = changed;
                            return values;
                        }
                    };
            assertThrows(IllegalArgumentException.class, () -> new CachedTwoPlayerCfrGame(source));
        }
    }

    @Test
    void constructionStillRequiresValidChancePerfectRecallBoundsAndExactlyTwoActors() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CachedTwoPlayerCfrGame(
                                new Hidden() {
                                    public List<
                                                    ChanceOutcome<
                                                            FiniteTwoPlayerSequenceFormTest.State>>
                                            chanceOutcomes(
                                                    FiniteTwoPlayerSequenceFormTest.State state) {
                                        return List.of(
                                                new ChanceOutcome<>(
                                                        super.chanceOutcomes(state)
                                                                .getFirst()
                                                                .state(),
                                                        .9));
                                    }
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CachedTwoPlayerCfrGame(
                                new Hidden() {
                                    public int currentPlayer(
                                            FiniteTwoPlayerSequenceFormTest.State state) {
                                        return state.stage() == 0 ? -1 : 0;
                                    }
                                }));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CachedTwoPlayerCfrGame(
                                new Hidden() {
                                    public double[] terminalUtilities(
                                            FiniteTwoPlayerSequenceFormTest.State state) {
                                        return new double[] {Double.NaN, 0, 0, 0, 0, 0};
                                    }
                                }));
        // Reusing a previous own information set is imperfect recall, not a cacheable game.
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new CachedTwoPlayerCfrGame(
                                new Hidden() {
                                    public int currentPlayer(
                                            FiniteTwoPlayerSequenceFormTest.State state) {
                                        return state.stage() == 0 ? -1 : 0;
                                    }

                                    public String informationSet(
                                            FiniteTwoPlayerSequenceFormTest.State state) {
                                        return "same-own-row";
                                    }
                                }));
    }
}
