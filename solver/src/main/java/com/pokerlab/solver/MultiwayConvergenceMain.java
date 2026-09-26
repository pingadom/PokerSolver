package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Re-solves one saved payoff game at requested budgets without recomputing boards. */
public final class MultiwayConvergenceMain {
    private MultiwayConvergenceMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Usage: MultiwayConvergenceMain <pack.json> <iterations,iterations,...>");
        var pack = MultiwayPackJson.read(Files.readString(Path.of(args[0])));
        var game = pack.rebuildGame();
        System.out.println("pack_hash=" + MultiwayPackJson.contentHash(pack));
        System.out.println("iterations,nash_conv_bb,max_payoff_se_bb,seconds");
        for (String budget : args[1].split(",")) {
            int iterations = Integer.parseInt(budget);
            long started = System.nanoTime();
            var solution =
                    new MultiPlayerCfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
            var report = MultiwayCallBestResponse.assess(game, solution);
            System.out.printf(
                    Locale.ROOT,
                    "%d,%.12f,%.12f,%.3f%n",
                    iterations,
                    report.nashConvBb(),
                    game.maximumTerminalPayoffStandardErrorBb(),
                    (System.nanoTime() - started) / 1e9);
        }
    }
}
