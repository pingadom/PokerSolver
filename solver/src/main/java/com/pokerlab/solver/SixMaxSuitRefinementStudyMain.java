package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Bounded offline fresh solving and read-only evidence replay, never a page-triggered solve. */
public final class SixMaxSuitRefinementStudyMain {
    private SixMaxSuitRefinementStudyMain() {}

    public static void main(String[] args) throws Exception {
        boolean solve = args.length == 10 && args[0].equals("solve");
        boolean audit = args.length == 6 && args[0].equals("audit");
        boolean replay = args.length == 6 && args[0].equals("replay");
        if (!solve && !audit && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxSuitRefinementStudyMain solve <source> <rank-table> <suit-table> <checkpoint> <report> <iterations> <source-ranks> <bet-fraction> <NONE|FIXED_UTILITY> | <audit|replay> <source> <rank-table> <suit-table> <checkpoint> <report>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(5)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        int iterations = solve ? Integer.parseInt(args[6]) : 0;
        if (solve && (iterations < 1 || iterations > SixMaxSuitRefinementStudy.MAX_ITERATIONS))
            throw new IllegalArgumentException("Study requires 1–1000 iterations");
        var pruning =
                solve
                        ? MultiPlayerCfrSolver.InactivePruning.valueOf(args[9])
                        : MultiPlayerCfrSolver.InactivePruning.NONE;
        List<Integer> ranks =
                solve
                        ? Arrays.stream(args[7].split(",", -1))
                                .map(String::trim)
                                .map(Integer::parseInt)
                                .toList()
                        : List.of();
        double fraction = solve ? Double.parseDouble(args[8]) : 0;
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var table = SixMaxSuitRefinementPayoffTable.read(paths.get(2), source, parent);
        SixMaxSuitRefinementStudy.Report report;
        if (solve) {
            var result =
                    SixMaxSuitRefinementStudy.solve(
                            source,
                            parent,
                            table,
                            SixMaxRankTextureStudy.select(source, ranks, fraction),
                            iterations,
                            pruning);
            SixMaxSuitRefinementStudy.write(
                    paths.get(3), result.checkpoint(), source, parent, table);
            report = result.report();
            System.out.println(
                    "TRAVERSAL "
                            + result.traversal()
                            + " pruned suffix roots "
                            + result.inactiveUtilityPrunedNodes());
        } else {
            var cp = SixMaxSuitRefinementStudy.read(paths.get(3), source, parent, table);
            report =
                    replay
                            ? SixMaxSuitRefinementStudy.replay(
                                    paths.get(4), source, parent, table, cp)
                            : SixMaxSuitRefinementStudy.assess(source, parent, table, cp);
        }
        if (!replay) SixMaxSuitRefinementStudy.writeReport(paths.get(4), report);
        System.out.println(
                "VALIDATION_ONLY suit-refinement parent gap "
                        + report.jointlySolvedDiagnostics()
                                .parentWitness()
                                .parentQuality()
                                .nashConvBb()
                        + " conditional maximum "
                        + report.jointlySolvedDiagnostics().summary().largestConditionalGapBb());
    }
}
