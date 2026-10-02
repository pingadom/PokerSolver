package com.pokerlab.solver;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Reproducible independent-board holdout of one frozen policy on the exact 64-deal fixture. */
public final class SixMaxPreflopHoldoutMain {
    private SixMaxPreflopHoldoutMain() {}

    public static void main(String[] args) {
        if (args.length != 6)
            throw new IllegalArgumentException(
                    "Usage: SixMaxPreflopHoldoutMain <hierarchy|mixed-pairs> "
                            + "<training-boards-per-deal> "
                            + "<holdout-boards-per-deal> <training-seed> "
                            + "<holdout-seed,holdout-seed,...> <cfr-iterations>");
        int trainingBoards = positive(args[1]);
        int holdoutBoards = positive(args[2]);
        long trainingSeed = Long.parseLong(args[3]);
        int iterations = positive(args[5]);
        List<Long> holdoutSeeds =
                List.of(args[4].split(",", -1)).stream()
                        .map(String::trim)
                        .map(Long::parseLong)
                        .toList();
        Set<Long> seeds = new HashSet<>(holdoutSeeds);
        if (seeds.size() != holdoutSeeds.size() || seeds.contains(trainingSeed))
            throw new IllegalArgumentException("Training and holdout seeds must be distinct");

        var rules = new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));
        var ranges =
                switch (args[0]) {
                    case "hierarchy" -> SixMaxExpandedConvergenceMain.fixtureRanges();
                    case "mixed-pairs" -> SixMaxExpandedConvergenceMain.mixedPairRanges();
                    default -> throw new IllegalArgumentException("Unknown fixture: " + args[0]);
                };
        var training =
                new SixMaxPreflopCheckdownGame(
                        rules,
                        ranges,
                        CashRakeRule.none(),
                        new SharedBoardMultiwayShowdownOracle(trainingBoards, trainingSeed));
        var policy =
                new MultiPlayerCfrSolver<>(training, CfrSolver.Variant.CFR_PLUS).solve(iterations);
        var trainingReport = MultiPlayerInformationSetBestResponse.assess(training, policy);
        System.out.printf(
                Locale.ROOT,
                "fixture=%s chance_model=%s deals=%d public_states=%d information_sets=%d "
                        + "iterations=%d training_seed=%d training_boards_per_deal=%d "
                        + "training_max_payoff_se_bb=%.9f training_nash_conv_bb=%.9f%n",
                args[0],
                training.chanceModel(),
                training.chanceOutcomes(training.initialState()).size(),
                training.treeSummary().totalStates(),
                policy.strategy().size(),
                iterations,
                trainingSeed,
                trainingBoards,
                training.maximumTerminalPayoffStandardErrorBb(),
                trainingReport.nashConvBb());
        System.out.println(
                "holdout_seed,holdout_boards_per_deal,holdout_max_payoff_se_bb,"
                        + "holdout_nash_conv_bb,nash_conv_drift_bb,"
                        + "max_abs_profile_utility_drift_bb,max_abs_deviation_gain_drift_bb");
        for (long seed : holdoutSeeds) {
            var holdout =
                    new SixMaxPreflopCheckdownGame(
                            rules,
                            ranges,
                            CashRakeRule.none(),
                            new SharedBoardMultiwayShowdownOracle(holdoutBoards, seed));
            var report =
                    SixMaxPreflopPolicyHoldoutAudit.assess(
                            training, holdout, policy, trainingReport);
            System.out.printf(
                    Locale.ROOT,
                    "%d,%d,%.9f,%.9f,%.9f,%.9f,%.9f%n",
                    seed,
                    holdoutBoards,
                    report.holdoutMaximumTerminalPayoffStandardErrorBb(),
                    report.holdout().nashConvBb(),
                    report.holdout().nashConvBb() - report.training().nashConvBb(),
                    report.maximumAbsoluteProfileUtilityDriftBb(),
                    report.maximumAbsoluteDeviationGainDriftBb());
        }
    }

    private static int positive(String input) {
        int value = Integer.parseInt(input);
        if (value < 1) throw new IllegalArgumentException("Counts must be positive");
        return value;
    }
}
