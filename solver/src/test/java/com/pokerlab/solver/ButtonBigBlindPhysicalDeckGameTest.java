package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class ButtonBigBlindPhysicalDeckGameTest {
    @Test
    void lazySamplerMatchesEveryEnumeratedPhysicalChanceDistribution() {
        var game = ButtonBigBlindPhysicalDeckFixture.create();
        var root = game.chanceOutcomes(game.initialState());
        assertEquals(4, root.size());
        assertEquals(1, root.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        var dealt = root.getFirst().state();
        var called = game.afterAction(game.afterAction(dealt, "open3"), "call");
        var flops = game.chanceOutcomes(called);
        assertEquals(17_296, flops.size());
        assertEquals(
                17_296, flops.stream().map(outcome -> outcome.state().flop()).distinct().count());
        assertEquals(1, flops.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        for (double quantile : List.of(0.0, 0.25, 0.5, Math.nextDown(1.0))) {
            int index = (int) (quantile * flops.size());
            assertEquals(
                    flops.get(index), game.sampleChanceOutcome(called, quantile), "flop quantile");
        }
        assertThrows(IllegalArgumentException.class, () -> game.sampleChanceOutcome(called, 1));
        var flop = flops.getFirst().state();
        var turnNode = game.afterAction(game.afterAction(flop, "k"), "k");
        var turns = game.chanceOutcomes(turnNode);
        assertEquals(45, turns.size());
        assertEquals(1, turns.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertEquals(turns.get(22), game.sampleChanceOutcome(turnNode, 0.5));
        var turn = turns.getFirst().state();
        var riverNode = game.afterAction(game.afterAction(turn, "k"), "k");
        var rivers = game.chanceOutcomes(riverNode);
        assertEquals(44, rivers.size());
        assertEquals(1, rivers.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
        assertEquals(rivers.get(22), game.sampleChanceOutcome(riverNode, 0.5));
        var river = rivers.getFirst().state();
        var showdown = game.afterAction(game.afterAction(river, "k"), "k");
        assertTrue(game.isTerminal(showdown));
        assertTrue(Double.isFinite(game.terminalUtility(showdown)));
    }

    @Test
    void sharedRunoutMatchesRestrictedGameAccountingAndKeepsPrivateCardsHidden() {
        var physical = ButtonBigBlindPhysicalDeckFixture.create();
        var restricted = ButtonBigBlindResearchFixture.create(1, 2);
        var physicalDeal =
                physical.chanceOutcomes(physical.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(
                                state ->
                                        state.bigBlind().key().equals("Jc Jd")
                                                && state.button().key().equals("Kh Qh"))
                        .findFirst()
                        .orElseThrow();
        var restrictedDeal =
                restricted.chanceOutcomes(restricted.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(
                                state ->
                                        state.bigBlind().key().equals("Jc Jd")
                                                && state.button().key().equals("Kh Qh"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(6.5, physical.potBb());
        assertEquals(17.25, physical.maximumAbsoluteTerminalUtilityBb());
        assertEquals(
                restricted.terminalUtility(restricted.afterAction(restrictedDeal, "fold")),
                physical.terminalUtility(physical.afterAction(physicalDeal, "fold")));
        var physicalCalled =
                physical.afterAction(physical.afterAction(physicalDeal, "open3"), "call");
        var restrictedCalled =
                restricted.afterAction(restricted.afterAction(restrictedDeal, "open3"), "call");
        var board = List.of(Card.parse("2c"), Card.parse("7d"), Card.parse("Th"));
        var physicalFlop =
                physical.chanceOutcomes(physicalCalled).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> new HashSet<>(state.flop()).equals(new HashSet<>(board)))
                        .findFirst()
                        .orElseThrow();
        var restrictedFlop = restricted.chanceOutcomes(restrictedCalled).getFirst().state();
        assertEquals(
                restricted.terminalUtility(
                        restricted.afterAction(restricted.afterAction(restrictedFlop, "b"), "f")),
                physical.terminalUtility(
                        physical.afterAction(physical.afterAction(physicalFlop, "b"), "f")));
        var physicalTurnNode = physical.afterAction(physical.afterAction(physicalFlop, "k"), "k");
        var restrictedTurnNode =
                restricted.afterAction(restricted.afterAction(restrictedFlop, "k"), "k");
        var physicalTurn =
                physical.chanceOutcomes(physicalTurnNode).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.turn().equals(Card.parse("3s")))
                        .findFirst()
                        .orElseThrow();
        var restrictedTurn =
                restricted.chanceOutcomes(restrictedTurnNode).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.streetState().turn().equals(Card.parse("3s")))
                        .findFirst()
                        .orElseThrow();
        var physicalRiverNode = physical.afterAction(physical.afterAction(physicalTurn, "k"), "k");
        var restrictedRiverNode =
                restricted.afterAction(restricted.afterAction(restrictedTurn, "k"), "k");
        var physicalRiver =
                physical.chanceOutcomes(physicalRiverNode).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.river().equals(Card.parse("5c")))
                        .findFirst()
                        .orElseThrow();
        var restrictedRiver =
                restricted.chanceOutcomes(restrictedRiverNode).stream()
                        .map(ChanceOutcome::state)
                        .filter(state -> state.streetState().river().equals(Card.parse("5c")))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                restricted.terminalUtility(
                        restricted.afterAction(restricted.afterAction(restrictedRiver, "k"), "k")),
                physical.terminalUtility(
                        physical.afterAction(physical.afterAction(physicalRiver, "k"), "k")));
        var betCalled = physical.afterAction(physical.afterAction(physicalRiver, "b"), "c");
        assertTrue(
                Math.abs(physical.terminalUtility(betCalled))
                        <= physical.maximumAbsoluteTerminalUtilityBb());

        var otherButton =
                physical.chanceOutcomes(physical.initialState()).stream()
                        .map(ChanceOutcome::state)
                        .filter(
                                state ->
                                        state.bigBlind().equals(physicalDeal.bigBlind())
                                                && !state.button().equals(physicalDeal.button()))
                        .findFirst()
                        .orElseThrow();
        var samePublicFlop =
                new ButtonBigBlindPhysicalDeckGame.State(
                        otherButton.bigBlind(),
                        otherButton.button(),
                        "oc",
                        physicalFlop.flop(),
                        "",
                        null,
                        "",
                        null,
                        "");
        assertEquals(
                physical.informationSet(physicalFlop), physical.informationSet(samePublicFlop));
    }

    @Test
    void seededFullDeckSamplingIsReproducibleButLeavesAResearchOnlySparseProfile() {
        var game = ButtonBigBlindPhysicalDeckFixture.create();
        var solver =
                new CfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                        42);
        CfrSolution first = solver.solve(30);
        assertEquals(first, solver.solve(30));
        assertTrue(first.strategy().containsKey("1:P:BTN:Kh Qh"));
        assertTrue(first.strategy().containsKey("0:P:BB:open3:Jc Jd"));
        assertTrue(first.strategy().size() > 4);
        assertNotEquals(
                game.contentHash(),
                new ButtonBigBlindPhysicalDeckGame(
                                List.of(new WeightedCombo(Card.parse("Kh"), Card.parse("Qh"), 1)),
                                List.of(new WeightedCombo(Card.parse("Jc"), Card.parse("Jd"), 1)),
                                2,
                                4,
                                8)
                        .contentHash());
        assertEquals(
                game.contentHash(),
                new ButtonBigBlindPhysicalDeckGame(
                                List.of(
                                        new WeightedCombo(Card.parse("Kh"), Card.parse("Qh"), 1),
                                        new WeightedCombo(
                                                Card.parse("Ac"), Card.parse("Ad"), 0.25)),
                                List.of(
                                        new WeightedCombo(Card.parse("As"), Card.parse("Ks"), 1),
                                        new WeightedCombo(Card.parse("Jc"), Card.parse("Jd"), 1)),
                                2,
                                4,
                                8)
                        .contentHash());
    }

    @Test
    void sampledCheckdownTracksExactPhysicalDeckWithoutClaimingStrategyQuality() {
        var game = ButtonBigBlindPhysicalDeckFixture.create();
        var oracle = new ExactPreflopEquityOracle();
        var report = PhysicalConnectedChanceAudit.assess(game, 5_000, 42, oracle);
        assertEquals(report, PhysicalConnectedChanceAudit.assess(game, 5_000, 42, oracle));
        assertEquals(4, oracle.uniqueMatchupsEnumerated());
        assertEquals(4, report.matchups().size());
        assertEquals(0.196069, report.exactWeightedBb(), 1e-6);
        assertTrue(report.weightedSamplingStandardErrorBb() > 0);
        assertTrue(
                Math.abs(report.signedErrorBb())
                        < 5 * report.weightedSamplingStandardErrorBb() + 0.03);
        assertTrue(report.maxAbsoluteDealErrorBb() < 0.25);
    }
}
