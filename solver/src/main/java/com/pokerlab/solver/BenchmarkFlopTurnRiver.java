package com.pokerlab.solver;

/** Reproducible convergence and check-down chance-abstraction diagnostics. */
public final class BenchmarkFlopTurnRiver {
    private BenchmarkFlopTurnRiver() {}

    public static void main(String[] arguments) {
        if (arguments.length < 1
                || arguments.length > 2
                || arguments.length == 2 && !arguments[1].equals("--full-turn-deck"))
            throw new IllegalArgumentException(
                    "Usage: BenchmarkFlopTurnRiver <iterations> [--full-turn-deck]");
        int iterations = Integer.parseInt(arguments[0]);
        FlopTurnRiverSpot spot = FlopTurnRiverValidationSpot.create();
        if (arguments.length == 2) spot = spot.withFullTurnDeck();
        long start = System.nanoTime();
        FlopTurnChanceAudit.Report audit = FlopTurnChanceAudit.assess(spot);
        long audited = System.nanoTime();
        FlopTurnRiverGame game = spot.game();
        CfrSolution solution = new CfrSolver<>(game, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        long solved = System.nanoTime();
        HeadsUpBestResponse.Report response = HeadsUpBestResponse.assess(game, solution);
        long assessed = System.nanoTime();
        System.out.printf(
                "spot=%s iterations=%d infosets=%d profile=%.9f bb firstBR=%.9f bb "
                        + "secondBR=%.9f bb gap=%.9f bb solve=%.3f s assess=%.3f s%n",
                spot.contentHash(),
                iterations,
                solution.strategy().size(),
                response.profileValue(),
                response.firstBestResponse(),
                response.secondBestResponse(),
                response.gap(),
                (solved - audited) / 1e9,
                (assessed - solved) / 1e9);
        System.out.printf(
                "checkdown exact=%.9f bb restricted=%.9f bb signedError=%.9f bb "
                        + "maxDealError=%.9f bb deals=%d exactTurns=%d riverCards=%d audit=%.3f s%n",
                audit.exactCheckdownBb(),
                audit.restrictedCheckdownBb(),
                audit.weightedErrorBb(),
                audit.maxDealErrorBb(),
                audit.legalDeals(),
                audit.exactTurnCardsPerDeal(),
                audit.riverCardsPerTurn(),
                (audited - start) / 1e9);
    }
}
