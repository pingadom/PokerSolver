package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Offline fresh solve or diagnostic replay; payoff replay always precedes policy loading. */
public final class SixMaxHistoryPhysicalStudyMain {
    private SixMaxHistoryPhysicalStudyMain() {}

    public static void main(String[] args) throws Exception {
        boolean solve = args.length == 9 && args[0].equals("solve");
        boolean replay = args.length == 7 && args[0].equals("replay");
        if (!solve && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalStudyMain solve <source> <rank-table> <table> <checkpoint> <report> <traversal-evidence> <iterations> <NONE|FIXED_UTILITY> | replay <source> <rank-table> <table> <checkpoint> <report> <traversal-evidence>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(6)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        int iterations = solve ? Integer.parseInt(args[7]) : 0;
        var pruning =
                solve
                        ? MultiPlayerCfrSolver.InactivePruning.valueOf(args[8])
                        : MultiPlayerCfrSolver.InactivePruning.NONE;
        if (solve
                && (iterations < 1
                        || iterations > SixMaxHistoryPhysicalStudy.MAX_ITERATIONS
                        || Files.exists(paths.get(3))
                        || Files.exists(paths.get(4))
                        || Files.exists(paths.get(5))))
            throw new IllegalArgumentException(
                    "Fresh solve requires 1–1000 iterations and new outputs");
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var table = SixMaxHistoryPhysicalPayoffTable.replay(paths.get(2), source, parent);
        SixMaxHistoryPhysicalStudy.Report report;
        if (solve) {
            var result =
                    SixMaxHistoryPhysicalStudy.solve(source, parent, table, iterations, pruning);
            SixMaxHistoryPhysicalStudy.write(
                    paths.get(3), result.checkpoint(), source, parent, table);
            report = result.report();
            SixMaxHistoryPhysicalStudy.writeReport(paths.get(4), report);
            SixMaxHistoryPhysicalStudy.writeTrainingEvidence(paths.get(5), result);
            System.out.println(
                    "TRAVERSAL "
                            + result.traversal()
                            + " pruned suffix roots "
                            + result.inactiveUtilityPrunedNodes());
        } else {
            var cp = SixMaxHistoryPhysicalStudy.read(paths.get(3), source, parent, table);
            report = SixMaxHistoryPhysicalStudy.replay(paths.get(4), source, parent, table, cp);
            SixMaxHistoryPhysicalStudy.replayTrainingEvidence(
                    paths.get(5), source, parent, table, cp);
        }
        System.out.println(
                "VALIDATION_ONLY parent gap "
                        + report.jointlySolvedDiagnostics()
                                .parentWitness()
                                .parentQuality()
                                .nashConvBb()
                        + " literal coverage "
                        + report.physicalCoverage());
    }
}
