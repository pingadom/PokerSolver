package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Compares nested physical-flop coverage at fixed source histories and training budgets. */
public final class SixMaxFlopWidthStudyMain {
    public record Width(
            int flopsPerHistory,
            SixMaxContinuationStudyBudget.Cost cost,
            double sourcePhysicalBettingReachRatioToOneFlop,
            SixMaxReachedContinuationStudy.Reach sourcePolicyReach,
            List<SixMaxReachedContinuationStudy.SelectedHistory> selectedHistories,
            List<SixMaxReachedContinuationStudyMain.Run> runs) {
        public Width {
            selectedHistories = List.copyOf(selectedHistories);
            runs = List.copyOf(runs);
        }
    }

    public record Reference(
            SixMaxContinuationStudyBudget.Cost cost,
            SixMaxReachedContinuationStudy.Reach sourcePolicyReach,
            List<SixMaxReachedContinuationStudy.SelectedHistory> selectedHistories) {
        public Reference {
            selectedHistories = List.copyOf(selectedHistories);
        }
    }

    public record Artifact(
            String schemaVersion,
            String sourcePackHash,
            String sourceSpotHash,
            String publicationStatus,
            String executionStatus,
            String interpretation,
            int sourceIterations,
            double sourceCheckdownNashConvBb,
            long flopSelectionSeed,
            int requestedMaximumHistories,
            List<Integer> requestedFlopsPerHistory,
            List<Long> trainingSeeds,
            int jointIterations,
            int refinementIterations,
            SixMaxContinuationStudyBudget budget,
            double conditionalGapTargetBb,
            double parentNashConvToleranceBb,
            Reference oneFlopReference,
            List<Width> widths) {
        public Artifact {
            requestedFlopsPerHistory = List.copyOf(requestedFlopsPerHistory);
            trainingSeeds = List.copyOf(trainingSeeds);
            widths = List.copyOf(widths);
        }
    }

    private SixMaxFlopWidthStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 9 && (args.length != 10 || !args[9].equals("--plan-only")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxFlopWidthStudyMain <pack.json> <output.json> <training-seeds-csv> <joint-iterations> <refinement-iterations> <maximum-histories> <flop-seed> <flops-per-history-csv> <conditional-gap-target-bb> [--plan-only]");
        boolean planOnly = args.length == 10;
        var input = Path.of(args[0]);
        var output = Path.of(args[1]).toAbsolutePath();
        if (Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("Study must not overwrite its source");
        if (Files.size(input) > 16 * 1024 * 1024)
            throw new IllegalArgumentException("Pack exceeds 16 MiB limit");
        var seeds = Arrays.stream(args[2].split(",", -1)).map(Long::parseLong).toList();
        int jointIterations = Integer.parseInt(args[3]);
        int refinementIterations = Integer.parseInt(args[4]);
        int maximumHistories = Integer.parseInt(args[5]);
        long flopSeed = Long.parseLong(args[6]);
        var widths = Arrays.stream(args[7].split(",", -1)).map(Integer::parseInt).toList();
        double target = Double.parseDouble(args[8]);
        if (seeds.isEmpty()
                || seeds.size() > 3
                || seeds.stream().distinct().count() != seeds.size()
                || jointIterations < 1
                || jointIterations > 3000
                || refinementIterations < 1
                || refinementIterations > 500
                || maximumHistories < 1
                || maximumHistories > 4
                || widths.isEmpty()
                || widths.size() > 3
                || widths.stream().anyMatch(n -> n < 1 || n > 4)
                || !widths.equals(widths.stream().distinct().sorted().toList())
                || !Double.isFinite(target)
                || target <= 0)
            throw new IllegalArgumentException(
                    "Select 1–3 distinct seeds, 1–3000 joint iterations, 1–500 refinement iterations, 1–4 histories, 1–3 increasing widths in [1, 4] and a positive finite gap target");
        var pack = MultiwayPackJson.readFullRound(Files.readString(input));
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        // Check the widest request first, before any expensive training or output mutation.
        budget.validate(
                SixMaxReachedContinuationStudy.select(
                                pack.rebuildGame(),
                                pack.solution(),
                                maximumHistories,
                                widths.getLast(),
                                flopSeed,
                                budget)
                        .game());
        var referencePlan =
                SixMaxReachedContinuationStudy.select(
                        pack.rebuildGame(), pack.solution(), maximumHistories, 1, flopSeed, budget);
        var reference =
                new Reference(
                        budget.validate(referencePlan.game()),
                        referencePlan.sourceReach(),
                        referencePlan.selectedHistories());
        // Retain only the reference report, not its game caches, while training wider trees.
        referencePlan = null;
        var results = new ArrayList<Width>();
        for (int width : widths) {
            var plan =
                    SixMaxReachedContinuationStudy.select(
                            pack.rebuildGame(),
                            pack.solution(),
                            maximumHistories,
                            width,
                            flopSeed,
                            budget);
            var cost = budget.validate(plan.game());
            System.out.printf(
                    Locale.ROOT,
                    "flops_per_history=%d compatible_deal_flops=%d physical_betting_reach=%.12f%n",
                    width,
                    cost.compatibleDealFlops(),
                    plan.sourceReach().selectedPhysicalFlopProbability());
            var runs =
                    planOnly
                            ? List.<SixMaxReachedContinuationStudyMain.Run>of()
                            : SixMaxReachedContinuationStudyMain.train(
                                    plan.game(),
                                    seeds,
                                    jointIterations,
                                    List.of(refinementIterations),
                                    target,
                                    budget);
            results.add(
                    new Width(
                            width,
                            cost,
                            plan.sourceReach().selectedPhysicalFlopProbability()
                                    / reference
                                            .sourcePolicyReach()
                                            .selectedPhysicalFlopProbability(),
                            plan.sourceReach(),
                            plan.selectedHistories(),
                            runs));
        }
        var artifact =
                new Artifact(
                        "six-max-flop-width-study/v1",
                        MultiwayPackJson.fullRoundContentHash(pack),
                        pack.spotHash(),
                        "VALIDATION_ONLY",
                        planOnly ? "PLANNED" : "COMPLETED",
                        "Fixed source-ranked histories and nested unique physical-flop menus retain full counterfactual private support. Each width trains a fresh joint linear-CFR policy with the same seeds and iteration budget; equal seeds do not imply identical sampled trajectories after the tree changes. Coverage ratios use a width-one reference under the frozen source policy, while learned reach is reported separately. Exact tree preflight precedes training and must match full completion. Conditional CFR+ freezes each learned preflop posterior and is checked by exact conditional and six-player parent best responses. Parent scores across widths refer to different declared games; only before/after refinement within one run is paired. Other physical flops and multiway pots still check down. Flags do not certify safe replacement, unrestricted poker equilibrium or trainer admission.",
                        pack.solution().iterations(),
                        pack.nashConvBb(),
                        flopSeed,
                        maximumHistories,
                        widths,
                        seeds,
                        jointIterations,
                        refinementIterations,
                        budget,
                        target,
                        1e-9,
                        reference,
                        results);
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
    }
}
