package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxConnectedPreflopGameTest {
    static final List<PublicAction> HISTORY =
            List.of(
                    new PublicAction(Seat.UTG, "fold"), new PublicAction(Seat.HJ, "fold"),
                    new PublicAction(Seat.CO, "fold"), new PublicAction(Seat.BTN, "raise:3.0"),
                    new PublicAction(Seat.SB, "fold"), new PublicAction(Seat.BB, "call"));

    static SixMaxPreflopCheckdownGame base() {
        return new SixMaxPreflopCheckdownGame(
                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                List.of(
                        List.of(combo("As Ah", 1), combo("Ac Ad", 3)),
                        List.of(combo("Ks Kh", 1)),
                        List.of(combo("Qs Qh", 1)),
                        List.of(combo("Js Jh", 1)),
                        List.of(combo("Ts Th", 1)),
                        List.of(combo("9s 9h", 1))),
                CashRakeRule.none(),
                (hands, mask) -> {
                    double[] shares = new double[6];
                    for (int i = 0; i < 6; i++)
                        if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                    return MultiwayShowdownEstimate.certain(shares);
                });
    }

    static WeightedCombo combo(String cards, double weight) {
        var split = cards.split(" ");
        return new WeightedCombo(Card.parse(split[0]), Card.parse(split[1]), weight);
    }

    static SixMaxConnectedPreflopGame.Selection selection(String... boards) {
        return new SixMaxConnectedPreflopGame.Selection(
                HISTORY,
                Arrays.stream(boards)
                        .map(b -> Arrays.stream(b.split(" ")).map(Card::parse).toList())
                        .toList(),
                3.25,
                6.5,
                13);
    }

    static SixMaxConnectedPreflopGame game() {
        return new SixMaxConnectedPreflopGame(base(), List.of(selection("2d 3d 4d")));
    }

    @Test
    void counterfactualSupportKeepsZeroPolicyReachPrivateDeals() {
        var base = base();
        Map<String, Map<String, Double>> policy = new LinkedHashMap<>();
        for (var outcome : base.chanceOutcomes(base.initialState())) {
            var state = outcome.state();
            for (var action : HISTORY) {
                var weights = new LinkedHashMap<String, Double>();
                double chosen = action.seat() == Seat.UTG && state.dealIndex() == 0 ? 0 : 1;
                String other =
                        base.legalActions(state).stream()
                                .filter(a -> !a.equals(action.action()))
                                .findFirst()
                                .orElseThrow();
                for (String legal : base.legalActions(state))
                    weights.put(
                            legal,
                            legal.equals(action.action())
                                    ? chosen
                                    : legal.equals(other) ? 1 - chosen : 0);
                policy.put(base.currentPlayer(state) + ":" + base.informationSet(state), weights);
                state = base.afterAction(state, action.action());
            }
        }
        var conditioned = new SixMaxPolicyFlopTransition(base, new CfrSolution(1, policy), HISTORY);
        var support = SixMaxPolicyFlopTransition.counterfactualSupport(base, HISTORY);
        assertEquals(1, conditioned.deals().size());
        assertEquals(2, support.deals().size());
        assertEquals(
                SixMaxPolicyFlopTransition.ReachModel.COUNTERFACTUAL_PRIVATE_SUPPORT,
                support.reachModel());
        assertEquals(
                base.chanceOutcomes(base.initialState()).getFirst().probability(),
                support.deals().getFirst().probability(),
                1e-12);
        assertThrows(IllegalStateException.class, support::reachProbability);
        assertThrows(IllegalStateException.class, support::logReachProbability);
        var connected = new SixMaxConnectedPreflopGame(base, List.of(selection("2d 3d 4d")));
        assertEquals(2, connected.chanceOutcomes(connected.replayPreflop(HISTORY, 0)).size());
    }

    @Test
    void selectedBoardsKeepPhysicalProbabilityAndFoldedSeatBlockers() {
        var game = new SixMaxConnectedPreflopGame(base(), List.of(selection("As 2d 3d")));
        assertEquals(6, game.playerCount());
        assertEquals(2, game.chanceOutcomes(game.initialState()).size());
        for (var root : game.chanceOutcomes(game.initialState())) {
            var terminal = game.replayPreflop(HISTORY, root.state().preflop().dealIndex());
            var outcomes = game.chanceOutcomes(terminal);
            boolean blocked =
                    game.source()
                                    .dealtHands(root.state().preflop())
                                    .getFirst()
                                    .first()
                                    .equals(Card.parse("As"))
                            || game.source()
                                    .dealtHands(root.state().preflop())
                                    .getFirst()
                                    .second()
                                    .equals(Card.parse("As"));
            assertEquals(blocked ? 1 : 2, outcomes.size());
            assertEquals(1, outcomes.stream().mapToDouble(ChanceOutcome::probability).sum(), 1e-12);
            if (!blocked) {
                var selected = outcomes.getFirst();
                assertEquals(1.0 / 9880, selected.probability(), 1e-15);
                assertEquals(Seat.BB.ordinal(), game.currentPlayer(selected.state()));
                assertNotEquals(-1, selected.state().postflop().dealIndex());
                var turnChance =
                        game.afterAction(game.afterAction(selected.state(), "check"), "check");
                assertEquals(37, game.chanceOutcomes(turnChance).size());
                assertTrue(
                        game.chanceOutcomes(turnChance).stream()
                                .noneMatch(
                                        o -> o.state().postflop().turn().equals(Card.parse("Ac"))));
            }
        }
    }

    @Test
    void hiddenFoldedHandsRemainIndistinguishableAtPostflopDecision() {
        var game = game();
        var first = game.chanceOutcomes(game.replayPreflop(HISTORY, 0)).getFirst().state();
        var second = game.chanceOutcomes(game.replayPreflop(HISTORY, 1)).getFirst().state();
        assertEquals(game.informationSet(first), game.informationSet(second));
        assertTrue(game.informationSet(first).contains("9h 9s"));
        assertFalse(game.informationSet(first).contains("Ah As"));
        assertFalse(game.informationSet(first).contains("Jh Js"));
        var sourceRoot =
                game.source().chanceOutcomes(game.source().initialState()).getFirst().state();
        assertEquals(
                game.source().informationSet(sourceRoot),
                game.informationSet(game.chanceOutcomes(game.initialState()).getFirst().state()));
    }

    @Test
    void postflopFoldSettlesCorrectSeatsAndRefundsUncalledBet() {
        var game = game();
        var state = game.chanceOutcomes(game.replayPreflop(HISTORY, 0)).getFirst().state();
        state = game.afterAction(game.afterAction(state, "bet"), "fold");
        assertTrue(game.isTerminal(state));
        double[] values = game.terminalUtilities(state);
        assertEquals(0, Arrays.stream(values).sum(), 1e-12);
        assertEquals(3.5, values[Seat.BB.ordinal()], 1e-12);
        assertEquals(-3, values[Seat.BTN.ordinal()], 1e-12);
        assertEquals(-0.5, values[Seat.SB.ordinal()], 1e-12);
        values[0] = 123;
        assertEquals(0, game.terminalUtilities(state)[0], 1e-12);
    }

    @Test
    void forcedCheckdownExactlyRecoversEverySourceSeatDespiteSelectedFlop() {
        var game = game();
        var baseline =
                new MultiPlayerCfrSolver<>(game.source(), CfrSolver.Variant.CFR_PLUS).solve(2);
        var lifted = SixMaxConnectedPreflopAudit.liftCheckdown(game, baseline);
        assertArrayEquals(
                MultiPlayerStrategyEvaluator.utilities(game.source(), baseline),
                MultiPlayerStrategyEvaluator.utilities(game, lifted),
                1e-10);
        assertTrue(lifted.strategy().size() > baseline.strategy().size());
        assertTrue(SixMaxConnectedPreflopAudit.bettingProbability(game, baseline) > 0);
        assertTrue(SixMaxConnectedPreflopAudit.bettingProbability(game, baseline) < 1.0 / 9880);
    }

    @Test
    void unselectedFoldAndAllInPathsRetainOriginalUtilities() {
        var game = game();
        for (String firstAction : List.of("fold", "raise:100.0")) {
            var state = game.chanceOutcomes(game.initialState()).getFirst().state();
            state = game.afterAction(state, firstAction);
            while (!game.isTerminal(state)) {
                var legal = game.legalActions(state);
                state =
                        game.afterAction(
                                state,
                                firstAction.equals("fold") && legal.contains("check")
                                        ? "check"
                                        : "fold");
            }
            assertArrayEquals(
                    game.source().terminalUtilities(state.preflop()),
                    game.terminalUtilities(state));
        }
    }

    @Test
    void jointCfrUpdatesPreflopAndPostflopAndMeasuresAllSixDeviationValues() {
        var game = game();
        var solution = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(2);
        assertTrue(solution.strategy().keySet().stream().anyMatch(k -> k.contains(":postflop:")));
        assertTrue(solution.strategy().keySet().stream().anyMatch(k -> !k.contains(":postflop:")));
        var quality = MultiPlayerInformationSetBestResponse.assess(game, solution);
        assertEquals(6, quality.deviationGainsBb().size());
        assertTrue(quality.deviationGainsBb().stream().allMatch(v -> v >= -1e-9));
        assertEquals(
                0,
                quality.profileUtilitiesBb().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
        assertEquals(
                quality.nashConvBb(),
                quality.deviationGainsBb().stream().mapToDouble(Double::doubleValue).sum(),
                1e-9);
    }

    @Test
    void rejectsInvalidSelectionsAndForgedStateLinks() {
        var base = base();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxConnectedPreflopGame(base, List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxConnectedPreflopGame(
                                base, List.of(selection("2d 3d 4d", "4d 2d 3d"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxConnectedPreflopGame(
                                base, List.of(selection("2d 3d 4d"), selection("2c 3c 4c"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxConnectedPreflopGame(base, List.of(selection("Ks 3d 4d"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxPolicyFlopTransition.counterfactualSupport(
                                base, HISTORY.subList(0, 2)));
        var game = new SixMaxConnectedPreflopGame(base, List.of(selection("2d 3d 4d")));
        var post0 = game.chanceOutcomes(game.replayPreflop(HISTORY, 0)).getFirst().state();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.isTerminal(
                                new SixMaxConnectedPreflopGame.State(
                                        game.replayPreflop(HISTORY, 1).preflop(),
                                        0,
                                        post0.postflop(),
                                        false)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.isTerminal(
                                new SixMaxConnectedPreflopGame.State(
                                        game.chanceOutcomes(game.initialState())
                                                .getFirst()
                                                .state()
                                                .preflop(),
                                        -1,
                                        null,
                                        true)));
        assertThrows(
                IllegalArgumentException.class,
                () -> game.afterAction(game.initialState(), "fold"));
    }

    @Test
    void rejectsSampledPayoffsRakeAndExcessivePrivateSupport() {
        var source = base();
        var ranges = source.chanceOutcomes(source.initialState()).getFirst().state();
        var hands = source.dealtHands(ranges);
        var singletonRanges = hands.stream().map(List::of).toList();
        for (boolean sampled : List.of(false, true)) {
            var unsupported =
                    new SixMaxPreflopCheckdownGame(
                            source.rules(),
                            singletonRanges,
                            sampled ? CashRakeRule.none() : new CashRakeRule(0.05, 3, false),
                            (dealt, mask) -> {
                                double[] shares = new double[6];
                                double[] errors = new double[6];
                                for (int i = 0; i < 6; i++)
                                    if ((mask & 1 << i) != 0) {
                                        shares[i] = 1.0 / Integer.bitCount(mask);
                                        errors[i] = sampled ? 0.01 : 0;
                                    }
                                return new MultiwayShowdownEstimate(shares, errors, 100);
                            });
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new SixMaxConnectedPreflopGame(
                                    unsupported, List.of(selection("2d 3d 4d"))));
        }
        var expanded =
                new SixMaxPreflopCheckdownGame(
                        source.rules(),
                        List.of(
                                List.of(combo("As Ah", 1), combo("Ac Ad", 1), combo("2s 2h", 1)),
                                List.of(combo("Ks Kh", 1), combo("Kc Kd", 1)),
                                List.of(combo("Qs Qh", 1), combo("Qc Qd", 1)),
                                singletonRanges.get(3),
                                singletonRanges.get(4),
                                singletonRanges.get(5)),
                        CashRakeRule.none(),
                        (dealt, mask) -> {
                            double[] shares = new double[6];
                            for (int i = 0; i < 6; i++)
                                if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        assertEquals(12, expanded.chanceOutcomes(expanded.initialState()).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxConnectedPreflopGame(expanded, List.of(selection("2d 3d 4d"))));
        var otherHistory =
                List.of(
                        new PublicAction(Seat.UTG, "call"),
                        new PublicAction(Seat.HJ, "fold"),
                        new PublicAction(Seat.CO, "fold"),
                        new PublicAction(Seat.BTN, "fold"),
                        new PublicAction(Seat.SB, "fold"),
                        new PublicAction(Seat.BB, "check"));
        var extra =
                new SixMaxConnectedPreflopGame.Selection(
                        otherHistory, selection("2h 3h 4h").flops(), 1, 2, 4);
        var wider =
                new SixMaxConnectedPreflopGame(
                        source, List.of(selection("2d 3d 4d", "2c 3c 4c"), extra));
        assertEquals(2, wider.coverage().size());
        var boards =
                java.util.stream.IntStream.range(0, 8)
                        .mapToObj(
                                i ->
                                        List.of(
                                                Card.parse(i % 2 == 0 ? "2c" : "2d"),
                                                Card.parse(i / 2 % 2 == 0 ? "3c" : "3d"),
                                                Card.parse(i / 4 % 2 == 0 ? "4c" : "4d")))
                        .toList();
        var first = new SixMaxConnectedPreflopGame.Selection(HISTORY, boards, 1, 2, 4);
        var second = new SixMaxConnectedPreflopGame.Selection(otherHistory, boards, 1, 2, 4);
        var thirdHistory =
                List.of(
                        new PublicAction(Seat.UTG, "raise:3.0"),
                        new PublicAction(Seat.HJ, "fold"),
                        new PublicAction(Seat.CO, "fold"),
                        new PublicAction(Seat.BTN, "fold"),
                        new PublicAction(Seat.SB, "fold"),
                        new PublicAction(Seat.BB, "call"));
        var third =
                new SixMaxConnectedPreflopGame.Selection(
                        thirdHistory, List.of(boards.getFirst()), 1, 2, 4);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxConnectedPreflopGame(source, List.of(first, second, third)));
    }

    @Test
    void allInRunoutKeepsFoldedLossesAndDoesNotOfferFurtherActions() {
        var selected = selection("2d 3d 4d");
        var game =
                new SixMaxConnectedPreflopGame(
                        base(),
                        List.of(
                                new SixMaxConnectedPreflopGame.Selection(
                                        HISTORY, selected.flops(), 100, 100, 100)));
        var state = game.chanceOutcomes(game.replayPreflop(HISTORY, 0)).getFirst().state();
        state = game.afterAction(game.afterAction(state, "bet"), "call");
        assertEquals(-1, game.currentPlayer(state));
        assertEquals(37, game.chanceOutcomes(state).size());
        var turn = game.chanceOutcomes(state).getFirst().state();
        assertEquals(-1, game.currentPlayer(turn));
        assertEquals(36, game.chanceOutcomes(turn).size());
        var river = game.chanceOutcomes(turn).getFirst().state();
        assertTrue(game.isTerminal(river));
        double[] values = game.terminalUtilities(river);
        assertEquals(-0.5, values[Seat.SB.ordinal()], 1e-12);
        assertEquals(0, Arrays.stream(values).sum(), 1e-12);
        assertTrue(Math.abs(values[Seat.BB.ordinal()] - values[Seat.BTN.ordinal()]) > 190);
        assertThrows(IllegalArgumentException.class, () -> game.legalActions(turn));
    }

    @Test
    void twoLegalSelectedFlopsAreNotRenormalized() {
        var game =
                new SixMaxConnectedPreflopGame(base(), List.of(selection("2d 3d 4d", "2c 3c 4c")));
        var chance = game.chanceOutcomes(game.replayPreflop(HISTORY, 0));
        assertEquals(3, chance.size());
        assertEquals(1.0 / 9880, chance.get(0).probability(), 1e-15);
        assertEquals(1.0 / 9880, chance.get(1).probability(), 1e-15);
        assertEquals(1 - 2.0 / 9880, chance.get(2).probability(), 1e-15);
        assertNotEquals(
                game.informationSet(chance.get(0).state()),
                game.informationSet(chance.get(1).state()));
    }

    @Test
    void impossiblePhysicalRemainderIsRejectedInsteadOfPublishingBadPayoff() {
        var source = base();
        var ranges =
                source
                        .dealtHands(source.chanceOutcomes(source.initialState()).getFirst().state())
                        .stream()
                        .map(List::of)
                        .toList();
        var inconsistent =
                new SixMaxPreflopCheckdownGame(
                        source.rules(),
                        ranges,
                        CashRakeRule.none(),
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            shares[
                                            (mask & 1 << Seat.BB.ordinal()) != 0
                                                    ? Seat.BB.ordinal()
                                                    : Integer.numberOfTrailingZeros(mask)] =
                                    1;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxConnectedPreflopGame(inconsistent, List.of(selection("2d 3d 4d"))));
    }

    @Test
    void chanceControlVariateUsesExactPreflopCheckdownWithoutBecomingATerminal() {
        var game = game();
        var selected = game.replayPreflop(HISTORY, 0);
        var expected = game.source().terminalUtilities(selected.preflop());
        for (int seat = 0; seat < 6; seat++)
            assertEquals(expected[seat], game.chanceBaselineUtility(selected, seat), 1e-12);
        assertFalse(game.isTerminal(selected));
        assertThrows(IllegalArgumentException.class, () -> game.terminalUtilities(selected));
        assertThrows(IllegalArgumentException.class, () -> game.chanceBaselineUtility(selected, 6));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        game.chanceBaselineUtility(
                                game.chanceOutcomes(selected).getFirst().state(), 0));
        assertEquals(0, game.chanceBaselineUtility(game.initialState(), 0));
    }
}
