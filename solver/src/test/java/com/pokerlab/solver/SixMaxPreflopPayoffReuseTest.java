package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopPayoffReuseTest {
    static final String TIME = "2026-10-06T12:00:00Z";

    static SixMaxPreflopResearchSpot spot(
            List<List<WeightedCombo>> ranges, double raise, CashRakeRule rake) {
        return new SixMaxPreflopResearchSpot(
                "reuse-test",
                new SixMaxPreflopBetting.Rules(100, .5, List.of(raise)),
                ranges,
                rake,
                SixMaxPreflopResearchSpot.MANDATORY_CHECKDOWN);
    }

    static SixMaxPreflopSolutionPack source() {
        return SixMaxPreflopPackBuilder.build(
                spot(SixMaxPreflopConvergenceMain.ranges("button-mix"), 100, CashRakeRule.none()),
                3,
                CfrSolver.Variant.CFR_PLUS,
                (hands, mask) -> {
                    double[] shares = new double[6];
                    shares[Integer.numberOfTrailingZeros(mask)] = 1;
                    return new MultiwayShowdownEstimate(
                            shares, new double[6], SixMaxPreflopSolutionPack.EXACT_BOARDS_PER_DEAL);
                },
                MultiwaySolutionPack.EXACT_ENUMERATION,
                0,
                TIME);
    }

    @Test
    void recomputesCommitmentsRakeAndStrategyWithoutTransferringSourcePolicy() {
        var source = source();
        var original = MultiwayPackJson.writeFullRound(source);
        var target = spot(source.spot().ranges(), 9, new CashRakeRule(.05, 1, true));
        var result =
                SixMaxPreflopPayoffReuse.buildExact(
                        source, target, 7, CfrSolver.Variant.CFR_PLUS, TIME);
        var pack = result.pack();
        assertEquals(7, pack.solution().iterations());
        assertNotEquals(source.solution().strategy().keySet(), pack.solution().strategy().keySet());
        assertNotEquals(source.spotHash(), pack.spotHash());
        assertEquals(
                MultiwayPackJson.fullRoundContentHash(source),
                result.provenance().sourcePackHash());
        assertEquals(target.contentHash(), result.provenance().targetSpotHash());
        assertEquals(114, result.provenance().reusedPayoffEntries());
        assertEquals(2, result.provenance().jointDeals());
        for (int i = 0; i < source.payoffs().size(); i++) {
            var before = source.payoffs().get(i);
            var after = pack.payoffs().get(i);
            assertEquals(before.dealtCombos(), after.dealtCombos());
            assertEquals(before.activeMask(), after.activeMask());
            assertArrayEquals(before.estimate().shares(), after.estimate().shares());
            assertEquals(before.estimate().trials(), after.estimate().trials());
        }
        var oldGame = source.rebuildGame();
        var newGame = pack.rebuildGame();
        var oldLeaf = buttonBigBlind(oldGame, 100);
        var newLeaf = buttonBigBlind(newGame, 9);
        assertEquals(
                SixMaxPreflopBetting.Status.ALL_IN_SHOWDOWN,
                oldGame.publicBettingState(oldLeaf).status());
        assertEquals(
                SixMaxPreflopBetting.Status.POSTFLOP_CONTINUATION_REQUIRED,
                newGame.publicBettingState(newLeaf).status());
        assertEquals(100.5, oldGame.terminalUtilities(oldLeaf)[3], 1e-12);
        assertEquals(9.5 - .925, newGame.terminalUtilities(newLeaf)[3], 1e-12);
        assertEquals(-9, newGame.terminalUtilities(newLeaf)[5], 1e-12);
        assertEquals(original, MultiwayPackJson.writeFullRound(source));
        assertEquals(
                MultiwayPackJson.writeFullRound(pack),
                MultiwayPackJson.writeFullRound(
                        MultiwayPackJson.readFullRound(MultiwayPackJson.writeFullRound(pack))));
    }

    private static SixMaxPreflopCheckdownGame.State buttonBigBlind(
            SixMaxPreflopCheckdownGame game, double raise) {
        var state = game.chanceOutcomes(game.initialState()).getFirst().state();
        for (var action : List.of("fold", "fold", "fold", "raise:" + raise, "fold", "call"))
            state = game.afterAction(state, action);
        assertTrue(game.isTerminal(state));
        return state;
    }

    @Test
    void reweightsIdenticalPhysicalSupportWithFreshChanceProbabilities() {
        var source = source();
        var ranges = new ArrayList<>(source.spot().ranges());
        var combo = ranges.get(3).getFirst();
        ranges.set(
                3,
                List.of(
                        new WeightedCombo(combo.first(), combo.second(), 3),
                        ranges.get(3).getLast()));
        var target = spot(ranges, 9, CashRakeRule.none());
        var result =
                SixMaxPreflopPayoffReuse.buildExact(
                        source, target, 2, CfrSolver.Variant.VANILLA, TIME);
        var game = result.pack().rebuildGame();
        assertEquals(.75, game.chanceOutcomes(game.initialState()).getFirst().probability(), 1e-12);
        assertEquals(.25, game.chanceOutcomes(game.initialState()).getLast().probability(), 1e-12);
        assertEquals(
                .5,
                source.rebuildGame()
                        .chanceOutcomes(source.rebuildGame().initialState())
                        .getFirst()
                        .probability(),
                1e-12);
        assertEquals(MultiwaySolutionPack.SOLVER_VERSION, result.pack().solverVersion());
        assertEquals(0, result.pack().maxTerminalPayoffSEBb());
    }

    @Test
    void rejectsAddedAndRemovedPhysicalWorldsRatherThanPruningOrInventingPayoffs() {
        var source = source();
        var removed = new ArrayList<>(source.spot().ranges());
        removed.set(3, List.of(removed.get(3).getFirst()));
        var missing =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxPreflopPayoffReuse.buildExact(
                                        source,
                                        spot(removed, 9, CashRakeRule.none()),
                                        2,
                                        CfrSolver.Variant.CFR_PLUS,
                                        TIME));
        assertTrue(missing.getMessage().contains("cannot be removed"));
        var added = new ArrayList<>(source.spot().ranges());
        added.set(0, List.of(new WeightedCombo(Card.parse("2c"), Card.parse("2d"), 1)));
        var foreign =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxPreflopPayoffReuse.buildExact(
                                        source,
                                        spot(added, 9, CashRakeRule.none()),
                                        2,
                                        CfrSolver.Variant.CFR_PLUS,
                                        TIME));
        assertTrue(foreign.getMessage().contains("missing from the source"));
    }

    @Test
    void requiresValidatedExactSourceAndValidSolveSettings() {
        var source = source();
        var target = spot(source.spot().ranges(), 9, CashRakeRule.none());
        var corrupt =
                new SixMaxPreflopSolutionPack(
                        source.schemaVersion(),
                        source.solverVersion(),
                        source.publicationStatus(),
                        source.generatedAt(),
                        source.spot(),
                        source.spotHash(),
                        source.payoffMethod(),
                        source.payoffSeed(),
                        source.solution(),
                        source.payoffs(),
                        source.nashConvBb() + 1,
                        source.maxTerminalPayoffSEBb());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopPayoffReuse.buildExact(
                                corrupt, target, 2, CfrSolver.Variant.CFR_PLUS, TIME));
        var sampled =
                SixMaxPreflopPackBuilder.build(
                        source.spot(),
                        2,
                        CfrSolver.Variant.CFR_PLUS,
                        new SharedBoardMultiwayShowdownOracle(2, 711),
                        SixMaxPreflopSolutionPack.SHARED_BOARD_MONTE_CARLO,
                        711,
                        TIME);
        var error =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxPreflopPayoffReuse.buildExact(
                                        sampled, target, 2, CfrSolver.Variant.CFR_PLUS, TIME));
        assertTrue(error.getMessage().contains("exact-enumeration"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPreflopPayoffReuse.buildExact(
                                source, target, 0, CfrSolver.Variant.CFR_PLUS, TIME));
        assertThrows(
                Exception.class,
                () ->
                        SixMaxPreflopPayoffReuse.buildExact(
                                source, target, 2, CfrSolver.Variant.CFR_PLUS, "yesterday"));
    }
}
