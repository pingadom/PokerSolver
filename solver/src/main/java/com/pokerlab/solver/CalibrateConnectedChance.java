package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Locale;

/** Seeded in-sample chance calibration plus a separate CFR solve of the selected abstract game. */
public final class CalibrateConnectedChance {
    private CalibrateConnectedChance() {}

    public static void main(String[] args) {
        if (args.length > 3)
            throw new IllegalArgumentException(
                    "Usage: CalibrateConnectedChance [seed] [candidateMenus] [iterations]");
        long seed = args.length >= 1 ? Long.parseLong(args[0]) : 42;
        int candidates = args.length >= 2 ? Integer.parseInt(args[1]) : 100;
        int iterations = args.length >= 3 ? Integer.parseInt(args[2]) : 100;
        if (iterations < 1) throw new IllegalArgumentException("Iterations must be positive");
        long started = System.nanoTime();
        var oracle = new ExactPreflopEquityOracle();
        var selection = ConnectedChanceCalibration.search(seed, candidates, oracle);
        var game = ButtonBigBlindResearchFixture.create(selection.flops(), selection.turns());
        var holdout =
                ConnectedChanceAudit.assess(
                        ButtonBigBlindResearchFixture.holdout(selection.flops(), selection.turns()),
                        oracle);
        var solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        var response = HeadsUpBestResponse.assess(game, solution);
        double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
        System.out.printf(
                Locale.ROOT,
                "Seed %d, %d accepted/%d rejected menus, %d CFR+ iterations, %.2fs%n",
                seed,
                selection.acceptedCandidates(),
                selection.rejectedCandidates(),
                iterations,
                seconds);
        System.out.println(
                "Flops: "
                        + selection.flops().stream().map(CalibrateConnectedChance::cards).toList());
        System.out.println("Turns: " + cards(selection.turns()));
        System.out.printf(
                Locale.ROOT,
                "Check-down error: signed %+.6fbb, mean absolute %.6fbb, max deal %.6fbb%n",
                selection.audit().signedErrorBb(),
                selection.audit().meanAbsoluteErrorBb(),
                selection.audit().maxAbsoluteDealErrorBb());
        System.out.printf(
                Locale.ROOT,
                "Unseen-combo check-down error: signed %+.6fbb, mean absolute %.6fbb, max deal %.6fbb%n",
                holdout.signedErrorBb(),
                holdout.meanAbsoluteErrorBb(),
                holdout.maxAbsoluteDealErrorBb());
        System.out.printf(
                Locale.ROOT,
                "Abstract-game gap %.6fbb, %d information sets, hash %s%n",
                response.gap(),
                solution.strategy().size(),
                game.contentHash());
        System.out.println(
                "In-sample check-down calibration is not validation of betting strategy error.");
        solution.strategy().entrySet().stream()
                .filter(entry -> entry.getKey().contains(":P:"))
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> System.out.println(entry.getKey() + " -> " + entry.getValue()));
    }

    private static String cards(List<Card> cards) {
        return cards.stream().map(Card::compact).toList().toString();
    }
}
