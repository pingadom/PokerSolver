package com.pokerlab.solver;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Offline, quality-gated whole rounds with explicit average-policy checkpoints. */
public final class SixMaxAlternatingContinuationStudyMain {
    public record FreshTrainingSettings(
            long seed, int jointIterations, int initialPostflopIterations) {}

    public record InitialTraining(
            long seed,
            int jointIterations,
            int initialPostflopIterations,
            int visitedInformationSets,
            int uniformlyCompletedInformationSets,
            MultiPlayerCfrSolver.Statistics traversal,
            SixMaxConditionalPostflopRefinement.Report refinement) {}

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String executionStatus,
            String executionMode,
            String interpretation,
            String sourcePackHash,
            String sourceSpotHash,
            long flopSelectionSeed,
            int requestedMaximumHistories,
            int flopsPerHistory,
            SixMaxContinuationStudyBudget budget,
            SixMaxContinuationStudyBudget.Cost cost,
            List<SixMaxReachedContinuationStudy.SelectedHistory> selectedHistories,
            SixMaxAlternatingContinuationSolver.Settings settings,
            FreshTrainingSettings freshTrainingSettings,
            InitialTraining initialTraining,
            String resumedSolutionHash,
            SixMaxAlternatingContinuationSolver.Report rounds) {
        public Artifact {
            selectedHistories = List.copyOf(selectedHistories);
        }
    }

    private SixMaxAlternatingContinuationStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 14
                && !(args.length == 15 && args[14].equals("--plan-only"))
                && !(args.length == 16 && args[14].equals("--resume")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxAlternatingContinuationStudyMain <pack.json> <report.json> <checkpoint-output.json> <seed> <joint-iterations> <initial-postflop-iterations> <preflop-iterations> <postflop-iterations> <max-rounds> <max-histories> <flop-seed> <flops-per-history> <conditional-target-bb> <minimum-improvement-bb> [--plan-only | --resume checkpoint.json]");
        boolean planOnly = args.length == 15;
        Path resume = args.length == 16 ? Path.of(args[15]) : null;
        var sourcePath = Path.of(args[0]);
        var reportPath = Path.of(args[1]);
        var checkpointPath = Path.of(args[2]);
        long seed = Long.parseLong(args[3]);
        int joint = Integer.parseInt(args[4]);
        int initialPost = Integer.parseInt(args[5]);
        var settings =
                new SixMaxAlternatingContinuationSolver.Settings(
                        Integer.parseInt(args[8]),
                        Integer.parseInt(args[6]),
                        Integer.parseInt(args[7]),
                        Double.parseDouble(args[12]),
                        Double.parseDouble(args[13]));
        int histories = Integer.parseInt(args[9]);
        long flopSeed = Long.parseLong(args[10]);
        int width = Integer.parseInt(args[11]);
        if (joint < 1 || joint > 3000 || initialPost < 1 || initialPost > 500)
            throw new IllegalArgumentException(
                    "Require joint iterations in [1,3000] and initial postflop in [1,500]");
        for (var pair :
                List.of(
                        new Path[] {sourcePath, reportPath},
                        new Path[] {sourcePath, checkpointPath},
                        new Path[] {reportPath, checkpointPath}))
            if (sameFile(pair[0], pair[1]))
                throw new IllegalArgumentException(
                        "Source, report and checkpoint output must be distinct files");
        if (resume != null && (sameFile(resume, sourcePath) || sameFile(resume, reportPath)))
            throw new IllegalArgumentException(
                    "Resume checkpoint cannot alias the source or report");
        if (Files.size(sourcePath) > 16L * 1024 * 1024)
            throw new IllegalArgumentException("Source pack exceeds 16 MiB limit");
        var source = MultiwayPackJson.readFullRound(Files.readString(sourcePath));
        var budget = SixMaxContinuationStudyBudget.widerFlops();
        var plan =
                SixMaxReachedContinuationStudy.select(
                        source.rebuildGame(),
                        source.solution(),
                        histories,
                        width,
                        flopSeed,
                        budget);
        var cost = budget.validate(plan.game());
        InitialTraining training = null;
        String resumedHash = null;
        SixMaxAlternatingContinuationSolver.Report rounds = null;
        if (!planOnly) {
            CfrSolution initial;
            SixMaxConnectedPreflopGame game;
            if (resume != null) {
                var loaded = SixMaxConnectedPolicyCheckpoint.read(resume, source);
                if (!loaded.snapshot().selections().equals(plan.game().selections())
                        || !loaded.snapshot().budget().equals(budget))
                    throw new IllegalArgumentException(
                            "Resume checkpoint must match the declared continuation menu and budget");
                game = loaded.game();
                initial = loaded.snapshot().policy();
                resumedHash = loaded.snapshot().solutionHash();
                log("RESUME_FRESH_AUDITS hash=" + resumedHash);
            } else {
                game = plan.game();
                log("JOINT_LINEAR_CFR seed=" + seed);
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
                    throw new IllegalStateException(
                            "Preflight count disagrees with completed tree");
                log("INITIAL_POSTFLOP_REFINEMENT");
                var refined =
                        SixMaxConditionalPostflopRefinement.refine(
                                game,
                                complete.solution(),
                                initialPost,
                                budget,
                                b -> log("initial flop=" + b.flop() + " status=" + b.status()));
                training =
                        new InitialTraining(
                                seed,
                                joint,
                                initialPost,
                                sampled.strategy().size(),
                                complete.addedInformationSets(),
                                solver.statistics(),
                                refined.report());
                initial = refined.candidate();
            }
            var initialAudit = SixMaxAlternatingContinuationSolver.audit(game, initial, budget);
            if (!initialAudit.everySelectedBranchReached()
                    || initialAudit.maximumConditionalGapBb() > settings.conditionalGapTargetBb())
                throw new IllegalArgumentException(
                        "Initial policy fails conditional target; no checkpoint replaced");
            if (resume == null || !sameFile(resume, checkpointPath))
                SixMaxConnectedPolicyCheckpoint.write(
                        checkpointPath,
                        SixMaxConnectedPolicyCheckpoint.capture(source, game, initial, budget),
                        source);
            var result =
                    SixMaxAlternatingContinuationSolver.solve(
                            game,
                            initial,
                            settings,
                            budget,
                            SixMaxAlternatingContinuationStudyMain::log,
                            (policy, round) -> {
                                try {
                                    SixMaxConnectedPolicyCheckpoint.write(
                                            checkpointPath,
                                            SixMaxConnectedPolicyCheckpoint.capture(
                                                    source, game, policy, budget),
                                            source);
                                } catch (java.io.IOException failure) {
                                    throw new java.io.UncheckedIOException(
                                            "Could not persist accepted round " + round.number(),
                                            failure);
                                }
                            });
            rounds = result.report();
        }
        var artifact =
                new Artifact(
                        "six-max-alternating-continuation-study/v1",
                        "VALIDATION_ONLY",
                        planOnly ? "PLANNED" : "COMPLETED",
                        resume == null ? "FRESH" : "RESUMED",
                        "Each whole round re-solves preflop against frozen exact continuation values, then re-solves postflop at the changed ranges. A candidate replaces the retained policy only when every selected branch is reached, conditional gaps meet the declared target and full six-seat NashConv improves materially. Stop at the first rejected round. Resume restores an explicit average policy and freshly audits it; it does not restore CFR regret tables. Unselected flops and multiway pots check down. These finite-game checks do not certify safe subgame solving, convergence, full cash poker or trainer admission.",
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        flopSeed,
                        histories,
                        width,
                        budget,
                        cost,
                        plan.selectedHistories(),
                        settings,
                        resume == null ? new FreshTrainingSettings(seed, joint, initialPost) : null,
                        training,
                        resumedHash,
                        rounds);
        var output = reportPath.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        new ObjectMapper()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writerWithDefaultPrettyPrinter()
                .writeValue(output.toFile(), artifact);
    }

    private static boolean sameFile(Path first, Path second) throws java.io.IOException {
        return first.toAbsolutePath().normalize().equals(second.toAbsolutePath().normalize())
                || (Files.exists(first) && Files.exists(second) && Files.isSameFile(first, second));
    }

    private static void log(String message) {
        System.out.println("alternating " + message);
    }
}
