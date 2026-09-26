package com.pokerlab.solver;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Compares forced check-down values in a connected research game with exhaustive physical five-card
 * boards. It diagnoses chance abstraction for one fixed policy; it is not a bound on strategic EV
 * or on the game's best-response gap.
 */
public final class ConnectedChanceAudit {
    public record Matchup(
            String bigBlindCombo,
            String buttonCombo,
            double dealProbability,
            double exactCheckdownBb,
            double abstractCheckdownBb,
            double errorBb) {}

    public record Report(
            String gameHash,
            double exactWeightedBb,
            double abstractWeightedBb,
            double signedErrorBb,
            double meanAbsoluteErrorBb,
            double maxAbsoluteDealErrorBb,
            List<Matchup> matchups) {}

    private ConnectedChanceAudit() {}

    public static Report assess(ButtonBigBlindContinuationGame game) {
        return assess(game, new ExactPreflopEquityOracle());
    }

    /** Reusing one exact oracle across candidate games avoids repeat board enumeration. */
    public static Report assess(
            ButtonBigBlindContinuationGame game, ExactPreflopEquityOracle oracle) {
        Objects.requireNonNull(game, "game");
        Objects.requireNonNull(oracle, "oracle");
        double potBb = game.flopSpots().get(0).potBb();
        List<Matchup> matchups = new ArrayList<>();
        double exactWeighted = 0;
        double abstractWeighted = 0;
        double meanAbsolute = 0;
        double maximum = 0;
        for (var deal : game.chanceOutcomes(game.initialState())) {
            var state = deal.state();
            EquityEstimate equity = oracle.estimate(state.bigBlind(), state.button());
            if (equity.standardError() != 0
                    || equity.trials() != ExactPreflopEquityOracle.BOARD_RUNOUTS)
                throw new IllegalStateException("Expected exact full-deck matchup equity");
            double exact = equity.equity() * potBb - potBb / 2;
            double abstractValue = abstractCheckdown(game, state);
            double error = abstractValue - exact;
            matchups.add(
                    new Matchup(
                            state.bigBlind().key(),
                            state.button().key(),
                            deal.probability(),
                            exact,
                            abstractValue,
                            error));
            exactWeighted += deal.probability() * exact;
            abstractWeighted += deal.probability() * abstractValue;
            meanAbsolute += deal.probability() * Math.abs(error);
            maximum = Math.max(maximum, Math.abs(error));
        }
        return new Report(
                game.contentHash(),
                exactWeighted,
                abstractWeighted,
                abstractWeighted - exactWeighted,
                meanAbsolute,
                maximum,
                List.copyOf(matchups));
    }

    private static double abstractCheckdown(
            ButtonBigBlindContinuationGame game, ButtonBigBlindContinuationGame.State deal) {
        var called = game.afterAction(game.afterAction(deal, "open3"), "call");
        double value = 0;
        for (var flop : game.chanceOutcomes(called)) {
            var flopChecked = checkStreet(game, flop.state());
            for (var turn : game.chanceOutcomes(flopChecked)) {
                var turnChecked = checkStreet(game, turn.state());
                for (var river : game.chanceOutcomes(turnChecked)) {
                    var riverChecked = checkStreet(game, river.state());
                    value +=
                            flop.probability()
                                    * turn.probability()
                                    * river.probability()
                                    * game.terminalUtility(riverChecked);
                }
            }
        }
        return value;
    }

    private static ButtonBigBlindContinuationGame.State checkStreet(
            ButtonBigBlindContinuationGame game, ButtonBigBlindContinuationGame.State state) {
        return game.afterAction(game.afterAction(state, "k"), "k");
    }
}
