package com.pokerlab.solver;

import static com.pokerlab.solver.PreflopAllInSpot.Seat.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MultiPlayerInformationSetBestResponseTest {
    private record TwoDecisionState(int hidden, String history) {}

    private static final class TwoDecisionGame implements MultiPlayerCfrGame<TwoDecisionState> {
        @Override
        public int playerCount() {
            return 2;
        }

        @Override
        public TwoDecisionState initialState() {
            return new TwoDecisionState(-1, "");
        }

        @Override
        public boolean isTerminal(TwoDecisionState state) {
            return state.hidden() >= 0 && state.history().length() == 2;
        }

        @Override
        public double[] terminalUtilities(TwoDecisionState state) {
            double hero =
                    switch (state.history()) {
                        case "AX" -> state.hidden() == 0 ? 4 : -2;
                        case "AY" -> 0;
                        case "BX" -> -1;
                        case "BY" -> state.hidden() == 0 ? 1 : 3;
                        default -> throw new IllegalArgumentException("Not a terminal history");
                    };
            return new double[] {hero, -hero};
        }

        @Override
        public int currentPlayer(TwoDecisionState state) {
            return state.hidden() == -1 ? -1 : 0;
        }

        @Override
        public List<String> legalActions(TwoDecisionState state) {
            return state.history().isEmpty() ? List.of("A", "B") : List.of("X", "Y");
        }

        @Override
        public String informationSet(TwoDecisionState state) {
            return state.history().isEmpty() ? "root" : "after:" + state.history();
        }

        @Override
        public TwoDecisionState afterAction(TwoDecisionState state, String action) {
            return new TwoDecisionState(state.hidden(), state.history() + action);
        }

        @Override
        public List<ChanceOutcome<TwoDecisionState>> chanceOutcomes(TwoDecisionState state) {
            return List.of(
                    new ChanceOutcome<>(new TwoDecisionState(0, ""), 0.5),
                    new ChanceOutcome<>(new TwoDecisionState(1, ""), 0.5));
        }
    }

    private static WeightedCombo combo(String first, String second, double weight) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), weight);
    }

    @Test
    void choosesBothActionsAtTheirInformationSetsWithoutSeeingHiddenChance() {
        var solution =
                new CfrSolution(
                        1,
                        Map.of(
                                "0:root", Map.of("A", 0.5, "B", 0.5),
                                "0:after:A", Map.of("X", 0.5, "Y", 0.5),
                                "0:after:B", Map.of("X", 0.5, "Y", 0.5)));
        var report = MultiPlayerInformationSetBestResponse.assess(new TwoDecisionGame(), solution);
        assertEquals(0.5, report.profileUtilitiesBb().get(0), 1e-12);
        assertEquals(2, report.bestResponseUtilitiesBb().get(0), 1e-12);
        assertEquals(1.5, report.deviationGainsBb().get(0), 1e-12);
        assertEquals(1.5, report.nashConvBb(), 1e-12);
        assertEquals("B", report.responseActions().get(0).get("root"));
        assertEquals("Y", report.responseActions().get(0).get("after:B"));
        assertEquals(0, report.deviationGainsBb().get(1), 1e-12);
    }

    @Test
    void agreesWithEstablishedForcedShoveBestResponse() {
        var game =
                new MultiwayPreflopCallGame(
                        List.of(UTG, BTN, BB),
                        List.of(
                                List.of(combo("AS", "AH", 1)),
                                List.of(combo("KS", "KH", 1), combo("QS", "QH", 2)),
                                List.of(combo("JS", "JH", 1))),
                        List.of(10.0, 0.0, 1.0),
                        10,
                        0,
                        (hands, mask) -> {
                            double[] shares = new double[3];
                            int active = Integer.bitCount(mask);
                            for (int seat = 0; seat < 3; seat++)
                                if ((mask & (1 << seat)) != 0) shares[seat] = 1.0 / active;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var solution = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(50);
        var oldReport = MultiwayCallBestResponse.assess(game, solution);
        var generic = MultiPlayerInformationSetBestResponse.assess(game, solution);
        assertEquals(oldReport.nashConvBb(), generic.nashConvBb(), 1e-8);
        for (int seat = 0; seat < game.playerCount(); seat++) {
            assertEquals(
                    oldReport.profileUtilitiesBb().get(seat),
                    generic.profileUtilitiesBb().get(seat),
                    1e-8);
            assertEquals(
                    oldReport.bestResponseUtilitiesBb().get(seat),
                    generic.bestResponseUtilitiesBb().get(seat),
                    1e-8);
        }
    }

    @Test
    void measuresSixSeatPreflopCheckdownPolicyWithoutChangingItsPayoffs() {
        var ranges = new ArrayList<List<WeightedCombo>>();
        ranges.add(List.of(combo("AS", "AH", 1)));
        ranges.add(List.of(combo("KS", "KH", 1), combo("8S", "8H", 3)));
        ranges.add(List.of(combo("QS", "QH", 1)));
        ranges.add(List.of(combo("JS", "JH", 1)));
        ranges.add(List.of(combo("TS", "TH", 1)));
        ranges.add(List.of(combo("9S", "9H", 1)));
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        ranges,
                        CashRakeRule.none(),
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            int active = Integer.bitCount(mask);
                            for (int seat = 0; seat < 6; seat++)
                                if ((mask & (1 << seat)) != 0) shares[seat] = 1.0 / active;
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var solution = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(20);
        var profile = MultiPlayerStrategyEvaluator.utilities(game, solution);
        var report = MultiPlayerInformationSetBestResponse.assess(game, solution);
        assertEquals(6, report.responseActions().size());
        assertTrue(report.responseActions().get(BB.ordinal()).size() > 1);
        assertTrue(report.nashConvBb() >= 0);
        double sum = 0;
        for (int seat = 0; seat < 6; seat++) {
            assertEquals(profile[seat], report.profileUtilitiesBb().get(seat), 1e-8);
            assertTrue(
                    report.bestResponseUtilitiesBb().get(seat)
                            >= report.profileUtilitiesBb().get(seat) - 1e-8);
            sum += report.deviationGainsBb().get(seat);
        }
        assertEquals(sum, report.nashConvBb(), 1e-8);
    }

    @Test
    void rakedMultiPlayerGameStillHasNonnegativeUnilateralGains() {
        var ranges =
                List.of(
                        List.of(combo("AS", "AH", 1)),
                        List.of(combo("KS", "KH", 1)),
                        List.of(combo("QS", "QH", 1)),
                        List.of(combo("JS", "JH", 1)),
                        List.of(combo("TS", "TH", 1)),
                        List.of(combo("9S", "9H", 1)));
        var game =
                new SixMaxPreflopCheckdownGame(
                        new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0)),
                        ranges,
                        new CashRakeRule(0.05, 1, true),
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            for (int seat = 0; seat < 6; seat++)
                                if ((mask & (1 << seat)) != 0)
                                    shares[seat] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var oneIteration = new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(1);
        var report = MultiPlayerInformationSetBestResponse.assess(game, oneIteration);
        assertTrue(report.profileUtilitiesBb().stream().mapToDouble(Double::doubleValue).sum() < 0);
        assertTrue(report.nashConvBb() >= 0);
        for (double gain : report.deviationGainsBb()) assertTrue(gain >= 0);
    }
}
