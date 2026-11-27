package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.Kind.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.OpenLimpRule.*;
import static com.pokerlab.solver.SixMaxPreflopBetting.RaiseSchedule.*;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.pokerlab.core.card.Card;
import java.util.List;
import org.junit.jupiter.api.Test;

class SixMaxPreflopWiderTreeTest {
    private static List<List<WeightedCombo>> oneDeal() {
        return List.of(
                List.of(new WeightedCombo(Card.parse("AS"), Card.parse("AH"), 1)),
                List.of(new WeightedCombo(Card.parse("KS"), Card.parse("KH"), 1)),
                List.of(new WeightedCombo(Card.parse("QS"), Card.parse("QH"), 1)),
                List.of(new WeightedCombo(Card.parse("JS"), Card.parse("JH"), 1)),
                List.of(new WeightedCombo(Card.parse("TS"), Card.parse("TH"), 1)),
                List.of(new WeightedCombo(Card.parse("9S"), Card.parse("9H"), 1)));
    }

    private static MultiwayShowdownOracle equalShareOracle() {
        return (hands, mask) -> {
            double[] shares = new double[6];
            double[] errors = new double[6];
            for (int seat = 0; seat < 6; seat++) {
                if ((mask & (1 << seat)) != 0) {
                    shares[seat] = 1.0 / Integer.bitCount(mask);
                    errors[seat] = 0.01 * (seat + 1);
                }
            }
            return new MultiwayShowdownEstimate(shares, errors, 100);
        };
    }

    private static SixMaxPreflopBetting.Move move(
            PreflopAllInSpot.Seat seat, SixMaxPreflopBetting.Kind kind, double amountBb) {
        return new SixMaxPreflopBetting.Move(seat, kind, amountBb);
    }

    @Test
    void jsonSerializationPreservesHistoricalCanonicalFormAndValidatesNewOptions()
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var legacy = new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 10.0), NEXT_TARGET);
        String legacyJson = mapper.writeValueAsString(legacy);
        assertFalse(legacyJson.contains("openLimpRule"));
        assertFalse(legacyJson.contains("maxRaisedPotPlayers"));
        assertEquals(legacy, mapper.readValue(legacyJson, SixMaxPreflopBetting.Rules.class));

        var shaped =
                new SixMaxPreflopBetting.Rules(
                        100, 0.5, List.of(3.0, 10.0, 22.0), NEXT_TARGET, SMALL_BLIND_ONLY, 2);
        String shapedJson = mapper.writeValueAsString(shaped);
        assertTrue(shapedJson.contains("\"openLimpRule\":\"SMALL_BLIND_ONLY\""));
        assertTrue(shapedJson.contains("\"maxRaisedPotPlayers\":2"));
        assertEquals(shaped, mapper.readValue(shapedJson, SixMaxPreflopBetting.Rules.class));

        assertThrows(
                JsonMappingException.class,
                () ->
                        mapper.readValue(
                                "{\"maxRaisedPotPlayers\":1,\"raiseToBb\":[3.0],\"smallBlindBb\":0.5,\"stackBb\":100.0}",
                                SixMaxPreflopBetting.Rules.class));
        assertThrows(
                JsonMappingException.class,
                () ->
                        mapper.readValue(
                                "{\"maxRaisedPotPlayers\":7,\"raiseToBb\":[3.0],\"smallBlindBb\":0.5,\"stackBb\":100.0}",
                                SixMaxPreflopBetting.Rules.class));
        assertThrows(
                JsonMappingException.class,
                () ->
                        mapper.readValue(
                                "{\"openLimpRule\":\"UNKNOWN\",\"raiseToBb\":[3.0],\"smallBlindBb\":0.5,\"stackBb\":100.0}",
                                SixMaxPreflopBetting.Rules.class));
    }

    @Test
    void openLimpAndMaxRaisedPotPlayersRulesEnforceExpectedLegalActions() {
        var sbOnly =
                new SixMaxPreflopBetting(
                        new SixMaxPreflopBetting.Rules(
                                100, 0.5, List.of(3.0, 10.0), NEXT_TARGET, SMALL_BLIND_ONLY, 2));
        var s = sbOnly.initialState();
        // UTG cannot open-limp
        assertFalse(s.legalActions().contains(move(UTG, CALL, 1)));
        assertTrue(s.legalActions().contains(move(UTG, FOLD, 0)));
        assertTrue(s.legalActions().contains(move(UTG, RAISE_TO, 3)));

        // Folded to SB: SB can complete under SMALL_BLIND_ONLY
        var toSb = s;
        for (var seat : List.of(UTG, HJ, CO, BTN)) toSb = sbOnly.apply(toSb, move(seat, FOLD, 0));
        assertTrue(toSb.legalActions().contains(move(SB, CALL, 1)));

        // Disallowed: SB cannot complete either
        var noLimp =
                new SixMaxPreflopBetting(
                        new SixMaxPreflopBetting.Rules(
                                100, 0.5, List.of(3.0, 10.0), NEXT_TARGET, DISALLOWED, 6));
        var toSbDisallowed = noLimp.initialState();
        for (var seat : List.of(UTG, HJ, CO, BTN))
            toSbDisallowed = noLimp.apply(toSbDisallowed, move(seat, FOLD, 0));
        assertFalse(toSbDisallowed.legalActions().contains(move(SB, CALL, 1)));

        // Max 2-way post-raise: UTG opens 3, HJ calls 3 -> CO cannot cold-call 3, can fold or 3-bet
        var opened = sbOnly.apply(s, move(UTG, RAISE_TO, 3));
        assertTrue(opened.legalActions().contains(move(HJ, CALL, 3)));
        var called = sbOnly.apply(opened, move(HJ, CALL, 3));
        assertFalse(called.legalActions().contains(move(CO, CALL, 3)));
        assertTrue(called.legalActions().contains(move(CO, FOLD, 0)));
        assertTrue(called.legalActions().contains(move(CO, RAISE_TO, 10)));
        // After CO 3-bets to 10, BTN can call 10 (making 2-way at 10)
        var threeBet = sbOnly.apply(called, move(CO, RAISE_TO, 10));
        assertTrue(threeBet.legalActions().contains(move(BTN, CALL, 10)));
    }

    @Test
    void shapedRulesFitThreeAndFourRaiseTargetsWithinExistingPublicStateCap() {
        var baselineTwo =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 10.0), NEXT_TARGET),
                        oneDeal(),
                        CashRakeRule.none(),
                        equalShareOracle());
        assertEquals(11_566, baselineTwo.treeSummary().totalStates());

        var noOpenLimpTwo =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(
                                100, 0.5, List.of(3.0, 10.0), NEXT_TARGET, SMALL_BLIND_ONLY, 6),
                        oneDeal(),
                        CashRakeRule.none(),
                        equalShareOracle());
        assertEquals(1_280, noOpenLimpTwo.treeSummary().totalStates());

        var noOpenLimpThree =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(
                                100,
                                0.5,
                                List.of(3.0, 10.0, 22.0),
                                NEXT_TARGET,
                                SMALL_BLIND_ONLY,
                                6),
                        oneDeal(),
                        CashRakeRule.none(),
                        equalShareOracle());
        assertEquals(10_342, noOpenLimpThree.treeSummary().totalStates());

        var headsUpFour =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(
                                100,
                                0.5,
                                List.of(3.0, 10.0, 22.0, 40.0),
                                NEXT_TARGET,
                                SMALL_BLIND_ONLY,
                                2),
                        oneDeal(),
                        CashRakeRule.none(),
                        equalShareOracle());
        assertEquals(6_823, headsUpFour.treeSummary().totalStates());
    }

    @Test
    void terminalTemplatesMatchAllInSidePotsExactlyAndThreeTargetTreeSolvesCleanly() {
        var rake = new CashRakeRule(0.05, 1.0, true);
        var oracle = equalShareOracle();
        var rules =
                new SixMaxPreflopBetting.Rules(
                        100, 0.5, List.of(3.0, 10.0, 22.0), NEXT_TARGET, SMALL_BLIND_ONLY, 2);
        var game = new SixMaxPreflopCheckdownGame(rules, oneDeal(), rake, oracle);
        assertEquals(1_727, game.treeSummary().totalStates());

        // Independently verify every terminal against AllInSidePots.settle
        var root = game.chanceOutcomes(game.initialState()).getFirst().state();
        int checked = verifyAllTerminals(game, root, rake, oracle);
        assertEquals(746, checked);

        // Solve end-to-end with CFR+ and verify best-response gap
        var solver = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS);
        var solution = solver.solve(50);
        var quality = MultiPlayerInformationSetBestResponse.assess(game, solution);
        assertTrue(Double.isFinite(quality.nashConvBb()));
        assertTrue(quality.nashConvBb() >= 0);
    }

    private static int verifyAllTerminals(
            SixMaxPreflopCheckdownGame game,
            SixMaxPreflopCheckdownGame.State state,
            CashRakeRule rake,
            MultiwayShowdownOracle oracle) {
        if (game.isTerminal(state)) {
            var betting = game.publicBettingState(state);
            double[] committed = new double[6];
            int activeMask = 0;
            for (var seat : PreflopAllInSpot.Seat.values()) {
                committed[seat.ordinal()] = betting.committedBb(seat);
                if (!betting.isFolded(seat)) activeMask |= 1 << seat.ordinal();
            }
            boolean flopDealt = betting.status() != SixMaxPreflopBetting.Status.UNCONTESTED;
            var expected =
                    AllInSidePots.settle(
                            committed,
                            activeMask,
                            0,
                            mask -> oracle.estimate(game.dealtHands(state), mask),
                            rake,
                            flopDealt);
            assertArrayEquals(expected.utilitiesBb(), game.terminalUtilities(state), 1e-12);
            assertEquals(expected.rakeBb(), game.terminalRakeBb(state), 1e-12);
            assertEquals(
                    expected.maximumStandardErrorBb(),
                    game.terminalMaximumPayoffStandardErrorBb(state),
                    1e-12);
            return 1;
        }
        int total = 0;
        for (String action : game.legalActions(state))
            total += verifyAllTerminals(game, game.afterAction(state, action), rake, oracle);
        return total;
    }
}
