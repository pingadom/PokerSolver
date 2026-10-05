package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Source-bound multi-history joint training and conditional refinement experiment. */
public final class SixMaxReachedContinuationStudyMain {
    public record Run(
            long trainingSeed,
            int jointIterations,
            String jointAlgorithm,
            String jointChanceTraversal,
            int visitedInformationSets,
            int uniformlyCompletedInformationSets,
            long completeTreeStates,
            MultiPlayerCfrSolver.Statistics traversal,
            SixMaxReachedContinuationStudy.Reach jointPolicyReach,
            Integer firstBudgetMeetingBothChecks,
            List<RefinementAttempt> refinementAttempts) {
        public Run {
            refinementAttempts = List.copyOf(refinementAttempts);
        }
    }

    public record RefinementAttempt(
            int iterations,
            double maximumConditionalGapBeforeBb,
            double maximumConditionalGapAfterBb,
            boolean everySelectedBranchRefined,
            boolean conditionalGapWithinTarget,
            boolean parentNashConvDidNotIncrease,
            SixMaxConditionalPostflopRefinement.Report refinement) {}

    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String publicationStatus,
            String interpretation,
            int sourceIterations,
            double sourceCheckdownNashConvBb,
            long flopSelectionSeed,
            int requestedMaximumHistories,
            int compatibleDealFlops,
            List<Integer> refinementBudgets,
            double conditionalGapTargetBb,
            double parentNashConvToleranceBb,
            SixMaxReachedContinuationStudy.Reach sourcePolicyReach,
            List<SixMaxReachedContinuationStudy.SelectedHistory> selectedHistories,
            List<Run> runs) {
        public Artifact {
            refinementBudgets = List.copyOf(refinementBudgets);
            selectedHistories = List.copyOf(selectedHistories);
            runs = List.copyOf(runs);
        }
    }

    private SixMaxReachedContinuationStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 8)
            throw new IllegalArgumentException(
                    "Usage: SixMaxReachedContinuationStudyMain <pack.json> <output.json> <training-seeds-csv> <joint-iterations> <refinement-budgets-csv> <maximum-histories> <flop-seed> <conditional-gap-target-bb>");
        var input = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Study must not overwrite its source");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var seeds = Arrays.stream(args[2].split(",", -1)).map(Long::parseLong).toList();
        int jointIterations = Integer.parseInt(args[3]);
        var refinementBudgets =
                Arrays.stream(args[4].split(",", -1)).map(Integer::parseInt).toList();
        int maximumHistories = Integer.parseInt(args[5]);
        long flopSeed = Long.parseLong(args[6]);
        double gapTarget = Double.parseDouble(args[7]);
        if (seeds.isEmpty()
                || seeds.size() > 3
                || seeds.stream().distinct().count() != seeds.size()
                || jointIterations < 1
                || jointIterations > 3000
                || refinementBudgets.isEmpty()
                || refinementBudgets.size() > 3
                || refinementBudgets.stream().anyMatch(n -> n < 1 || n > 500)
                || !refinementBudgets.equals(
                        refinementBudgets.stream().distinct().sorted().toList())
                || maximumHistories < 1
                || maximumHistories > 4
                || !Double.isFinite(gapTarget)
                || gapTarget <= 0)
            throw new IllegalArgumentException(
                    "Select 1–3 distinct seeds, 1–3000 joint iterations, 1–3 increasing refinement budgets in [1, 500], 1–4 histories and a positive finite gap target");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var plan =
                SixMaxReachedContinuationStudy.select(
                        pack.rebuildGame(), pack.solution(), maximumHistories, flopSeed);
        var game = plan.game();
        double parentTolerance = 1e-9;
        System.out.printf(
                Locale.ROOT,
                "histories=%d compatible_deal_flops=%d source_heads_up_reach=%.9f selected_history_reach=%.9f physical_betting_reach=%.9f%n",
                plan.selectedHistories().size(),
                plan.compatibleDealFlops(),
                plan.sourceReach().headsUpProbability(),
                plan.sourceReach().selectedHistoryProbability(),
                plan.sourceReach().selectedPhysicalFlopProbability());
        var runs =
                train(
                        game,
                        seeds,
                        jointIterations,
                        refinementBudgets,
                        gapTarget,
                        SixMaxContinuationStudyBudget.standard());
        var artifact =
                new Artifact(
                        "six-max-reached-continuation-study/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        "VALIDATION_ONLY",
                        "Select the highest-reach non-all-in heads-up histories from the source checkdown policy, retaining full counterfactual private support and one physical flop per history. Identical declared coverage is used across seeds. Joint linear CFR samples runouts; explicit uniform completion precedes exact conditional CFR+ and six-player parent best responses. Each refinement budget starts from the same completed joint policy, not a previous candidate; training is reused while before/after comparisons stay paired. Reach reports distinguish selected histories, physical betting flops, unselected heads-up histories and other terminal categories. Refinement leaves preflop unchanged. Quality flags describe this bounded research experiment, not safe subgame replacement, full cash-poker equilibrium or trainer admission.",
                        pack.solution().iterations(),
                        pack.nashConvBb(),
                        flopSeed,
                        maximumHistories,
                        plan.compatibleDealFlops(),
                        refinementBudgets,
                        gapTarget,
                        parentTolerance,
                        plan.sourceReach(),
                        plan.selectedHistories(),
                        runs);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
    }

    static List<Run> train(
            SixMaxConnectedPreflopGame game,
            List<Long> seeds,
            int jointIterations,
            List<Integer> refinementBudgets,
            double gapTarget,
            SixMaxContinuationStudyBudget budget) {
        var cost = budget.validate(game);
        double parentTolerance = 1e-9;
        System.out.printf(
                Locale.ROOT,
                "preflight_complete_tree_states=%d state_budget=%d%n",
                cost.completeTreeStates(),
                budget.maximumCompleteTreeStates());
        var runs = new ArrayList<Run>();
        for (long seed : seeds) {
            var solver =
                    new MultiPlayerCfrSolver<>(
                            game,
                            CfrSolver.Variant.VANILLA,
                            MultiPlayerCfrSolver.ChanceMode.SAMPLED_RUNOUTS,
                            seed,
                            0,
                            true,
                            true);
            var sampled = solver.solve(jointIterations);
            var complete =
                    MultiPlayerStrategyCompletion.uniformAtUnseen(
                            game, sampled, budget.maximumCompleteTreeStates());
            if (complete.visitedStates() != cost.completeTreeStates())
                throw new IllegalStateException("Preflight state count disagrees with completion");
            var reach = SixMaxReachedContinuationStudy.reach(game, complete.solution());
            var attempts = new ArrayList<RefinementAttempt>();
            for (int refinementIterations : refinementBudgets) {
                var refinement =
                        SixMaxConditionalPostflopRefinement.refine(
                                        game,
                                        complete.solution(),
                                        refinementIterations,
                                        budget,
                                        branch ->
                                                System.out.printf(
                                                        Locale.ROOT,
                                                        "seed=%d refinement_iterations=%d history=%s flop=%s status=%s before_gap_bb=%s after_gap_bb=%s%n",
                                                        seed,
                                                        refinementIterations,
                                                        branch.publicHistory(),
                                                        branch.flop(),
                                                        branch.status(),
                                                        branch.before() == null
                                                                ? "n/a"
                                                                : branch.before().gap(),
                                                        branch.after() == null
                                                                ? "n/a"
                                                                : branch.after().gap()))
                                .report();
                double before =
                        refinement.branches().stream()
                                .filter(b -> b.before() != null)
                                .mapToDouble(b -> b.before().gap())
                                .max()
                                .orElse(0);
                double after =
                        refinement.branches().stream()
                                .filter(b -> b.after() != null)
                                .mapToDouble(b -> b.after().gap())
                                .max()
                                .orElse(0);
                boolean refined =
                        refinement.branches().stream().allMatch(b -> b.status().equals("REFINED"));
                attempts.add(
                        new RefinementAttempt(
                                refinementIterations,
                                before,
                                after,
                                refined,
                                refined && after <= gapTarget,
                                refinement.nashConvChangeBb() <= parentTolerance,
                                refinement));
                System.out.printf(
                        Locale.ROOT,
                        "seed=%d refinement_iterations=%d learned_selected_history_reach=%.9f parent_before_bb=%.9f parent_after_bb=%.9f conditional_target_met=%s%n",
                        seed,
                        refinementIterations,
                        reach.selectedHistoryProbability(),
                        refinement.originalQuality().nashConvBb(),
                        refinement.candidateQuality().nashConvBb(),
                        refined && after <= gapTarget);
            }
            Integer firstBudget =
                    attempts.stream()
                            .filter(
                                    a ->
                                            a.conditionalGapWithinTarget()
                                                    && a.parentNashConvDidNotIncrease())
                            .map(RefinementAttempt::iterations)
                            .findFirst()
                            .orElse(null);
            runs.add(
                    new Run(
                            seed,
                            jointIterations,
                            "LINEAR_CFR",
                            "SAMPLED_RUNOUTS",
                            sampled.strategy().size(),
                            complete.addedInformationSets(),
                            complete.visitedStates(),
                            solver.statistics(),
                            reach,
                            firstBudget,
                            attempts));
        }
        return List.copyOf(runs);
    }
}
