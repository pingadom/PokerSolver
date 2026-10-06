package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Offline generation and independent saved-policy replay, never an HTTP request. */
public final class SixMaxTextureStudyMain {
    private SixMaxTextureStudyMain() {}

    public static void main(String[] args) throws Exception {
        boolean solve = (args.length == 8 || args.length == 9) && args[0].equals("solve");
        boolean audit = args.length == 5 && args[0].equals("audit");
        boolean repack = args.length == 5 && args[0].equals("repack");
        if (!solve && !audit && !repack)
            throw new IllegalArgumentException(
                    "Usage: SixMaxTextureStudyMain solve <source> <table> <checkpoint> <report> <iterations> <source-ranks> <bet-fraction> [NONE|FIXED_UTILITY] | audit <source> <table> <checkpoint> <report> | repack <source> <table> <checkpoint> <new-checkpoint>");
        var pruning =
                solve && args.length == 9
                        ? MultiPlayerCfrSolver.InactivePruning.valueOf(args[8])
                        : MultiPlayerCfrSolver.InactivePruning.NONE;
        var sourcePath = Path.of(args[1]).toAbsolutePath().normalize();
        var tablePath = Path.of(args[2]).toAbsolutePath().normalize();
        var checkpointPath = Path.of(args[3]).toAbsolutePath().normalize();
        var reportPath = Path.of(args[4]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(
                List.of(sourcePath, tablePath, checkpointPath, reportPath));
        int iterations = solve ? Integer.parseInt(args[5]) : 0;
        if (solve && (iterations < 1 || iterations > 3000))
            throw new IllegalArgumentException("Study requires 1–3000 iterations");
        var source = SixMaxTexturePayoffTableMain.source(sourcePath);
        var table = SixMaxTexturePayoffTable.read(tablePath, source);
        if (repack) {
            var checkpoint = SixMaxTextureStudy.read(checkpointPath, source, table);
            SixMaxTextureStudy.write(reportPath, checkpoint, source, table);
            System.out.println(
                    "VALIDATION_ONLY repacked " + checkpoint.solutionHash() + " " + reportPath);
            return;
        }
        SixMaxTextureStudy.Report report;
        if (solve) {
            var ranks =
                    Arrays.stream(args[6].split(",", -1))
                            .map(String::trim)
                            .map(Integer::parseInt)
                            .toList();
            var selections = SixMaxTextureStudy.select(source, ranks, Double.parseDouble(args[7]));
            var result = SixMaxTextureStudy.solve(source, table, selections, iterations, pruning);
            // Validation and report construction finish before either output is changed. Both are
            // individually atomic; interrupted paired exports can be recovered by audit replay.
            SixMaxTextureStudy.write(checkpointPath, result.checkpoint(), source, table);
            report = result.report();
            System.out.println("TRAVERSAL " + result.traversal());
            System.out.println(
                    "INACTIVE_PRUNING "
                            + pruning
                            + " skipped suffix roots "
                            + result.inactiveUtilityPrunedNodes());
        } else {
            var checkpoint = SixMaxTextureStudy.read(checkpointPath, source, table);
            report = SixMaxTextureStudy.assess(source, table, checkpoint);
        }
        SixMaxTexturePayoffTableMain.atomicWrite(reportPath, SixMaxTextureStudy.json(report));
        System.out.println(
                "VALIDATION_ONLY texture NashConv bb "
                        + report.jointlySolvedInTextureGame().nashConvBb()
                        + " "
                        + reportPath);
    }
}
