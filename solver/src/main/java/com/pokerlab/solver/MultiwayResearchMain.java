package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Locale;

/** Reproducible six-seat research run; no solution pack is published by this command. */
public final class MultiwayResearchMain {
    private MultiwayResearchMain() {}

    public static void main(String[] args) {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Usage: MultiwayResearchMain <iterations> <payoff-trials>");
        int iterations = Integer.parseInt(args[0]);
        int payoffTrials = Integer.parseInt(args[1]);
        long started = System.nanoTime();
        MultiwayPreflopCallGame game =
                new MultiwayPreflopCallGame(
                        List.of(PreflopAllInSpot.Seat.values()),
                        List.of(
                                List.of(combo("AS", "AH"), combo("KS", "KH")),
                                List.of(combo("AD", "AC"), combo("QS", "QH")),
                                List.of(combo("JS", "JH"), combo("TS", "TH")),
                                List.of(combo("9S", "9H"), combo("8S", "8H")),
                                List.of(combo("7S", "7H"), combo("6S", "6H")),
                                List.of(combo("5S", "5H"), combo("4S", "4H"))),
                        List.of(20.0, 0.0, 0.0, 0.0, 0.5, 1.0),
                        20,
                        0,
                        new SeededMultiwayShowdownOracle(payoffTrials, 42));
        long payoffsReady = System.nanoTime();
        CfrSolution solution =
                new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        MultiwayCallBestResponse.Report quality = MultiwayCallBestResponse.assess(game, solution);
        long finished = System.nanoTime();
        System.out.printf(
                Locale.ROOT,
                "Six-seat validation-only 20bb UTG shove/call game: %d joint deals, %d CFR+ iterations, %d board trials per active subset%n",
                game.chanceOutcomes(game.initialState()).size(),
                iterations,
                payoffTrials);
        System.out.printf(
                Locale.ROOT,
                "Sampled-table NashConv %.6f bb; maximum terminal payoff SE %.6f bb%n",
                quality.nashConvBb(),
                game.maximumTerminalPayoffStandardErrorBb());
        System.out.printf(
                Locale.ROOT,
                "Payoff generation %.2fs; solve and validation %.2fs%n",
                (payoffsReady - started) / 1e9,
                (finished - payoffsReady) / 1e9);
        for (int player = 0; player < game.playerCount(); player++)
            System.out.printf(
                    Locale.ROOT,
                    "%s profile EV %.4f bb, unilateral gain %.4f bb%n",
                    game.seats().get(player),
                    quality.profileUtilitiesBb().get(player),
                    quality.deviationGainsBb().get(player));
        System.out.println(
                "Research model only: sampled payoffs, synthetic ranges, no rake, forced shove, and no postflop play.");
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }
}
