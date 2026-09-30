package com.pokerlab.solver;

import java.util.List;
import java.util.Locale;

/** Reproducible response seed/budget sweep with validation selection and held-out confirmation. */
public final class BenchmarkPhysicalResponseStability {
    private BenchmarkPhysicalResponseStability() {}

    public static void main(String[] args) {
        if (args.length > 10)
            throw new IllegalArgumentException(
                    "Usage: BenchmarkPhysicalResponseStability [baseline-iterations] [short-response-iterations] [long-response-iterations] [validation-trials] [confirmation-trials] [seed] [3x3|5x5] [average|both] [sampled|exact-root] [sampled-eval|stratified-eval]");
        int baselineIterations = args.length >= 1 ? Integer.parseInt(args[0]) : 10_000;
        int shortBudget = args.length >= 2 ? Integer.parseInt(args[1]) : 10_000;
        int longBudget = args.length >= 3 ? Integer.parseInt(args[2]) : 50_000;
        int validationTrials = args.length >= 4 ? Integer.parseInt(args[3]) : 20_000;
        int confirmationTrials = args.length >= 5 ? Integer.parseInt(args[4]) : 50_000;
        long seed = args.length >= 6 ? Long.parseLong(args[5]) : 42;
        var profile =
                args.length >= 7
                        ? ButtonBigBlindRangeValidationFixture.RangeProfile.parse(args[6])
                        : ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        List<PhysicalResponseStabilityAudit.PolicyVariant> variants =
                args.length >= 8 && args[7].equals("both")
                        ? List.of(
                                PhysicalResponseStabilityAudit.PolicyVariant.AVERAGE,
                                PhysicalResponseStabilityAudit.PolicyVariant.FINAL_REGRET)
                        : List.of(PhysicalResponseStabilityAudit.PolicyVariant.AVERAGE);
        if (args.length >= 8 && !args[7].equals("both") && !args[7].equals("average"))
            throw new IllegalArgumentException("Policy variants must be average or both");
        var chanceMode =
                args.length >= 9
                        ? switch (args[8]) {
                            case "sampled" -> FixedOpponentResponseCfr.ChanceMode.SAMPLED_ALL;
                            case "exact-root" -> FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT;
                            default ->
                                    throw new IllegalArgumentException(
                                            "Chance mode must be sampled or exact-root");
                        }
                        : FixedOpponentResponseCfr.ChanceMode.SAMPLED_ALL;
        var evaluationMode =
                args.length == 10
                        ? switch (args[9]) {
                            case "sampled-eval" ->
                                    PhysicalResponseHeldOutAudit.EvaluationMode.SAMPLED_ROOT;
                            case "stratified-eval" ->
                                    PhysicalResponseHeldOutAudit.EvaluationMode.STRATIFIED_ROOT;
                            default ->
                                    throw new IllegalArgumentException(
                                            "Evaluation mode must be sampled-eval or stratified-eval");
                        }
                        : PhysicalResponseHeldOutAudit.EvaluationMode.SAMPLED_ROOT;
        if (baselineIterations < 1 || baselineIterations > 100_000)
            throw new IllegalArgumentException("Baseline iterations must be in 1-100000");
        if (shortBudget >= longBudget)
            throw new IllegalArgumentException("Short response budget must be below long budget");
        var game =
                ButtonBigBlindRangeValidationFixture.create(
                        ButtonBigBlindPhysicalDeckGame.InformationMode.COARSE_BOARD_BUCKETS,
                        profile);
        var baseline =
                new CfrSolver<>(
                                game,
                                CfrSolver.Variant.VANILLA,
                                CfrSolver.ChanceMode.SAMPLED_AFTER_ROOT,
                                seed + 100_003)
                        .solve(baselineIterations);
        int rootDeals = game.chanceOutcomes(game.initialState()).size();
        long dealsPerIteration =
                chanceMode == FixedOpponentResponseCfr.ChanceMode.EXACT_ROOT ? rootDeals : 1;
        System.out.printf(
                Locale.ROOT,
                "Response stability %s: baseline %d iterations, seed %d, %d information sets; %d root deals; response budgets %d/%d (%d/%d private-deal traversals), two training seeds, variants %s, chance mode %s, evaluation mode %s, validation %d and confirmation %d deals per candidate%n",
                profile,
                baselineIterations,
                seed + 100_003,
                baseline.strategy().size(),
                rootDeals,
                shortBudget,
                longBudget,
                shortBudget * dealsPerIteration,
                longBudget * dealsPerIteration,
                variants,
                chanceMode,
                evaluationMode,
                validationTrials,
                confirmationTrials);
        for (int target = 0; target <= 1; target++) {
            var report =
                    PhysicalResponseStabilityAudit.assess(
                            game,
                            baseline,
                            target,
                            List.of(shortBudget, longBudget),
                            List.of(seed + 200_003 + target, seed + 200_103 + target),
                            validationTrials,
                            confirmationTrials,
                            seed + 300_003 + target,
                            seed + 400_003 + target,
                            variants,
                            chanceMode,
                            evaluationMode);
            System.out.printf(
                    Locale.ROOT,
                    "%s: validation seed %d, confirmation seed %d; selected candidate %d by validation gain only (%d validation batches, %d confirmation batches)%n",
                    target == 0 ? "BB" : "BTN",
                    report.validationSeed(),
                    report.confirmationSeed(),
                    report.selectedIndex() + 1,
                    report.selected().validation().independentBatches(),
                    report.selected().confirmation().independentBatches());
            for (int index = 0; index < report.candidates().size(); index++) {
                var candidate = report.candidates().get(index);
                var confirmation = candidate.confirmation();
                System.out.printf(
                        Locale.ROOT,
                        "  %d%s budget %d seed %d %s keys %d missing-opponent %d queries/%d keys; validation gain %+.4fbb (SE %.4f), confirmation gain %+.4fbb (SE %.4f, 95%% [%.4f, %.4f]), fallback paths %d/%d%n",
                        index + 1,
                        index == report.selectedIndex() ? "*" : " ",
                        candidate.responseIterations(),
                        candidate.responseSeed(),
                        candidate.variant(),
                        candidate.learnedInformationSets(),
                        candidate.missingOpponentQueries(),
                        candidate.missingOpponentInformationSets(),
                        candidate.validation().responseGainBb(),
                        candidate.validation().pairedStandardErrorBb(),
                        confirmation.responseGainBb(),
                        confirmation.pairedStandardErrorBb(),
                        confirmation.approximateGainLower95Bb(),
                        confirmation.approximateGainUpper95Bb(),
                        confirmation.responseFallbackTrajectories(),
                        confirmation.trials());
            }
        }
        System.out.println(
                "Only the validation-selected candidate has a prespecified confirmation result. The other confirmation rows describe sensitivity and are not alternate winners. Intervals cover confirmation sampling only, not training variability or the wider search; none is a certified best response or Nash gap.");
    }
}
