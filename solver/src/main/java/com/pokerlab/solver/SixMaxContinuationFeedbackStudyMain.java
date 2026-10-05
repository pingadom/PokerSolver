package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** Offline joint -> postflop -> preflop -> optional postflop alternating study. */
public final class SixMaxContinuationFeedbackStudyMain {
    public record Run(
            long trainingSeed,
            int jointIterations,
            int visitedInformationSets,
            int uniformlyCompletedInformationSets,
            MultiPlayerCfrSolver.Statistics jointTraversal,
            SixMaxPreflopContinuationFeedback.Report unrefinedContinuationControl,
            SixMaxConditionalPostflopRefinement.Report initialPostflopRefinement,
            SixMaxPreflopContinuationFeedback.Report preflopFeedback,
            double maximumPreflopFrequencyDifferenceFromControl,
            SixMaxConditionalPostflopRefinement.Report finalPostflopRefinement,
            String finalSolutionHash,
            double finalMaximumConditionalGapBb,
            boolean everySelectedBranchReached,
            boolean finalConditionalGapWithinTarget,
            double finalParentNashConvChangeFromJointBb,
            boolean finalParentNashConvDidNotIncreaseFromJoint) {}

    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String publicationStatus,
            String executionStatus,
            String interpretation,
            long flopSelectionSeed,
            int requestedMaximumHistories,
            int flopsPerHistory,
            int jointIterations,
            int initialPostflopIterations,
            int preflopIterations,
            int finalPostflopIterations,
            List<Long> trainingSeeds,
            SixMaxContinuationStudyBudget.Cost cost,
            SixMaxContinuationStudyBudget budget,
            double conditionalGapTargetBb,
            double parentNashConvToleranceBb,
            SixMaxReachedContinuationStudy.Reach sourcePolicyReach,
            List<SixMaxReachedContinuationStudy.SelectedHistory> selectedHistories,
            List<Run> runs) {
        public Artifact {
            trainingSeeds = List.copyOf(trainingSeeds);
            selectedHistories = List.copyOf(selectedHistories);
            runs = List.copyOf(runs);
        }
    }

    private SixMaxContinuationFeedbackStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 11 && (args.length != 12 || !args[11].equals("--plan-only")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxContinuationFeedbackStudyMain <pack.json> <output.json> <training-seeds-csv> <joint-iterations> <initial-postflop-iterations> <preflop-iterations> <final-postflop-iterations-or-zero> <maximum-histories> <flop-seed> <flops-per-history> <conditional-gap-target-bb> [--plan-only]");
        boolean planOnly = args.length == 12;
        var input = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Study must not overwrite its source");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var seeds = Arrays.stream(args[2].split(",", -1)).map(Long::parseLong).toList();
        int joint = Integer.parseInt(args[3]);
        int initialPost = Integer.parseInt(args[4]);
        int pre = Integer.parseInt(args[5]);
        int finalPost = Integer.parseInt(args[6]);
        int histories = Integer.parseInt(args[7]);
        long flopSeed = Long.parseLong(args[8]);
        int width = Integer.parseInt(args[9]);
        double target = Double.parseDouble(args[10]);
        if (seeds.isEmpty()
                || seeds.size() > 3
                || seeds.stream().distinct().count() != seeds.size()
                || joint < 1
                || joint > 3000
                || initialPost < 1
                || initialPost > 500
                || pre < 1
                || pre > 3000
                || finalPost < 0
                || finalPost > 500
                || histories < 1
                || histories > 4
                || width < 1
                || width > 4
                || !Double.isFinite(target)
                || target <= 0)
            throw new IllegalArgumentException(
                    "Select 1–3 distinct seeds, joint/preflop budgets in [1, 3000], initial postflop in [1, 500], final postflop in [0, 500], histories/width in [1, 4] and a positive finite gap target");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var plan =
                SixMaxReachedContinuationStudy.select(
                        pack.rebuildGame(), pack.solution(), histories, width, flopSeed, budget);
        var cost = budget.validate(plan.game());
        var runs = new ArrayList<Run>();
        if (!planOnly)
            for (long seed : seeds)
                runs.add(
                        train(
                                plan.game(),
                                seed,
                                joint,
                                initialPost,
                                pre,
                                finalPost,
                                target,
                                budget,
                                cost));
        var artifact =
                new Artifact(
                        "six-max-continuation-feedback-study/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        "VALIDATION_ONLY",
                        planOnly ? "PLANNED" : "COMPLETED",
                        "Joint linear CFR samples runouts and is explicitly completed. A matched preflop CFR+ control freezes the unrefined joint continuations. The candidate first refines postflop, integrates exact continuation values for every original private deal and public history, then re-solves all six seats' preflop decisions with exhaustive CFR+. Postflop rows remain fixed during feedback; changed reach and conditional posteriors are re-audited, with optional postflop refinement at the new ranges. Projected best responses allow only preflop deviations; full parent audits also allow postflop deviations. Quality flags are separate and do not certify safe replacement, alternating convergence, full cash-poker equilibrium or trainer admission. Unselected flops and multiway pots still check down.",
                        flopSeed,
                        histories,
                        width,
                        joint,
                        initialPost,
                        pre,
                        finalPost,
                        seeds,
                        cost,
                        budget,
                        target,
                        1e-9,
                        plan.sourceReach(),
                        plan.selectedHistories(),
                        runs);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
    }

    private static Run train(
            SixMaxConnectedPreflopGame game,
            long seed,
            int joint,
            int initialPost,
            int pre,
            int finalPost,
            double target,
            SixMaxContinuationStudyBudget budget,
            SixMaxContinuationStudyBudget.Cost cost) {
        log(seed, "JOINT_LINEAR_CFR");
        var solver =
                new MultiPlayerCfrSolver<>(
                        game,
                        CfrSolver.Variant.VANILLA,
                        MultiPlayerCfrSolver.ChanceMode.SAMPLED_RUNOUTS,
                        seed,
                        0,
                        true,
                        true);
        var sampled = solver.solve(joint);
        var complete =
                MultiPlayerStrategyCompletion.uniformAtUnseen(
                        game, sampled, budget.maximumCompleteTreeStates());
        if (complete.visitedStates() != cost.completeTreeStates())
            throw new IllegalStateException("Preflight count disagrees with complete tree");
        log(seed, "UNREFINED_CONTINUATION_PREFLOP_CONTROL");
        var control =
                SixMaxPreflopContinuationFeedback.solve(game, complete.solution(), pre, budget);
        log(seed, "INITIAL_POSTFLOP_REFINEMENT");
        var initial =
                SixMaxConditionalPostflopRefinement.refine(
                        game, complete.solution(), initialPost, budget, progress(seed));
        log(seed, "REFINED_CONTINUATION_PREFLOP_FEEDBACK");
        var feedback =
                SixMaxPreflopContinuationFeedback.solve(game, initial.candidate(), pre, budget);
        double controlDifference = 0;
        for (var row :
                SixMaxPreflopContinuationFeedback.preflopPolicy(feedback.candidate())
                        .strategy()
                        .entrySet())
            for (var action : row.getValue().entrySet())
                controlDifference =
                        Math.max(
                                controlDifference,
                                Math.abs(
                                        action.getValue()
                                                - control.candidate()
                                                        .strategy()
                                                        .get(row.getKey())
                                                        .get(action.getKey())));
        var candidate = feedback.candidate();
        SixMaxConditionalPostflopRefinement.Report finalReport = null;
        double maximum =
                feedback.report().candidateConditionalPostflop().stream()
                        .mapToDouble(b -> b.bestResponse().gap())
                        .max()
                        .orElse(0);
        boolean allReached =
                feedback.report().candidateConditionalPostflop().size()
                        == game.selections().stream().mapToInt(s -> s.flops().size()).sum();
        double parent = feedback.report().candidateParentQuality().nashConvBb();
        if (finalPost > 0) {
            log(seed, "FINAL_POSTFLOP_REFINEMENT_AT_CHANGED_RANGES");
            var result =
                    SixMaxConditionalPostflopRefinement.refine(
                            game, candidate, finalPost, budget, progress(seed));
            candidate = result.candidate();
            finalReport = result.report();
            maximum =
                    finalReport.branches().stream()
                            .filter(b -> b.after() != null)
                            .mapToDouble(b -> b.after().gap())
                            .max()
                            .orElse(0);
            allReached =
                    finalReport.branches().stream().allMatch(b -> b.status().equals("REFINED"));
            parent = finalReport.candidateQuality().nashConvBb();
        }
        double parentChange = parent - initial.report().originalQuality().nashConvBb();
        System.out.printf(
                Locale.ROOT,
                "seed=%d final_parent_gap_bb=%.12f final_conditional_gap_bb=%.12f refined_vs_control_max_preflop_change=%.12f%n",
                seed,
                parent,
                maximum,
                controlDifference);
        return new Run(
                seed,
                joint,
                sampled.strategy().size(),
                complete.addedInformationSets(),
                solver.statistics(),
                control.report(),
                initial.report(),
                feedback.report(),
                controlDifference,
                finalReport,
                SixMaxConnectedPostflopAudit.solutionHash(candidate),
                maximum,
                allReached,
                allReached && maximum <= target,
                parentChange,
                parentChange <= 1e-9);
    }

    private static void log(long seed, String stage) {
        System.out.printf(Locale.ROOT, "seed=%d stage=%s%n", seed, stage);
    }

    private static Consumer<SixMaxConditionalPostflopRefinement.Branch> progress(long seed) {
        return b ->
                System.out.printf(
                        Locale.ROOT,
                        "seed=%d history=%s flop=%s status=%s after_gap_bb=%s%n",
                        seed,
                        b.publicHistory(),
                        b.flop(),
                        b.status(),
                        b.after() == null ? "n/a" : b.after().gap());
    }
}
