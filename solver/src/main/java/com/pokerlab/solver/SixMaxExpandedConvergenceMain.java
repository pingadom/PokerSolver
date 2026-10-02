package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.util.List;
import java.util.Locale;

/** Compares exact range chance with empirical deal chance on the same bounded public game. */
public final class SixMaxExpandedConvergenceMain {
    private SixMaxExpandedConvergenceMain() {}

    public static void main(String[] args) {
        if (args.length != 4)
            throw new IllegalArgumentException(
                    "Usage: SixMaxExpandedConvergenceMain "
                            + "<exact|accepted-deal-draws> <board-trials-per-deal> <seed> "
                            + "<iterations,iterations,...>");
        boolean exactChance = args[0].equals("exact");
        int draws = exactChance ? 0 : Integer.parseInt(args[0]);
        int boards = Integer.parseInt(args[1]);
        long seed = Long.parseLong(args[2]);
        var budgets = SixMaxPreflopConvergenceMain.parseBudgets(args[3]);
        var ranges =
                List.of(
                        List.of(combo("AS", "AH"), combo("AD", "AC")),
                        List.of(combo("KS", "KH"), combo("KD", "KC")),
                        List.of(combo("QS", "QH"), combo("QD", "QC")),
                        List.of(combo("JS", "JH"), combo("JD", "JC")),
                        List.of(combo("TS", "TH"), combo("TD", "TC")),
                        List.of(combo("9S", "9H"), combo("9D", "9C")));
        var sample =
                exactChance
                        ? null
                        : SixMaxJointDealSampler.sample(
                                ranges, draws, Math.multiplyExact(draws, 20), seed);
        var oracle = new SharedBoardMultiwayShowdownOracle(boards, seed ^ 0x5f3759dfL);
        var rules = new SixMaxPreflopBetting.Rules(100, 0.5, List.of(100.0));
        var game =
                exactChance
                        ? new SixMaxPreflopCheckdownGame(rules, ranges, CashRakeRule.none(), oracle)
                        : SixMaxPreflopCheckdownGame.fromSampledDeals(
                                rules, sample, CashRakeRule.none(), oracle);
        double fixtureChanceTotalVariation =
                exactChance
                        ? 0
                        : 0.5
                                * ((64 - sample.deals().size()) / 64.0
                                        + sample.deals().stream()
                                                .mapToDouble(
                                                        deal ->
                                                                Math.abs(
                                                                        deal.occurrences()
                                                                                        / (double)
                                                                                                draws
                                                                                - 1.0 / 64))
                                                .sum());
        System.out.printf(
                Locale.ROOT,
                "chance_model=%s accepted_draws=%d rejected_draws=%d unique_deals=%d "
                        + "fixture_chance_tv=%.9f public_states=%d boards_evaluated=%d "
                        + "max_payoff_se_bb=%.9f chance_sampling_error=%s%n",
                game.chanceModel(),
                exactChance ? 0 : sample.acceptedDraws(),
                exactChance ? 0 : sample.rejectedDraws(),
                game.chanceOutcomes(game.initialState()).size(),
                fixtureChanceTotalVariation,
                game.treeSummary().totalStates(),
                oracle.boardsEvaluated(),
                game.maximumTerminalPayoffStandardErrorBb(),
                exactChance ? "NONE" : "NOT_ESTIMATED");
        System.out.println(
                "iterations,information_sets,nash_conv_bb,max_deviation_bb,profile_total_bb");
        for (var row : SixMaxPreflopConvergenceAudit.run(game, budgets))
            System.out.printf(
                    Locale.ROOT,
                    "%d,%d,%.9f,%.9f,%.9f%n",
                    row.iterations(),
                    row.informationSets(),
                    row.nashConvBb(),
                    row.largestDeviationBb(),
                    row.profileTotalBb());
    }

    private static WeightedCombo combo(String first, String second) {
        return new WeightedCombo(Card.parse(first), Card.parse(second), 1);
    }
}
