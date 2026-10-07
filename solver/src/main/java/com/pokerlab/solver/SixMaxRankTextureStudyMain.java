package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Bounded offline solving and read-only replay; no trainer or HTTP acceptance path. */
public final class SixMaxRankTextureStudyMain {
    private SixMaxRankTextureStudyMain() {}

    public static void main(String[] args) throws Exception {
        boolean solve = args.length == 9 && args[0].equals("solve");
        boolean audit = args.length == 5 && args[0].equals("audit");
        boolean repack = args.length == 5 && args[0].equals("repack");
        if (!solve && !audit && !repack)
            throw new IllegalArgumentException(
                    "Usage: SixMaxRankTextureStudyMain solve <source> <table> <checkpoint> <report> <iterations> <source-ranks> <bet-fraction> <NONE|FIXED_UTILITY> | audit <source> <table> <checkpoint> <report> | repack <source> <table> <checkpoint> <new-checkpoint>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(4)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        int iterations = solve ? Integer.parseInt(args[5]) : 0;
        if (solve && (iterations < 1 || iterations > SixMaxRankTextureStudy.MAX_ITERATIONS))
            throw new IllegalArgumentException("Study requires 1–1000 iterations");
        var pruning =
                solve
                        ? MultiPlayerCfrSolver.InactivePruning.valueOf(args[8])
                        : MultiPlayerCfrSolver.InactivePruning.NONE;
        List<Integer> ranks =
                solve
                        ? Arrays.stream(args[6].split(",", -1))
                                .map(String::trim)
                                .map(Integer::parseInt)
                                .toList()
                        : List.of();
        double fraction = solve ? Double.parseDouble(args[7]) : 0;
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var table = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        if (repack) {
            var cp = SixMaxRankTextureStudy.read(paths.get(2), source, table);
            SixMaxRankTextureStudy.write(paths.get(3), cp, source, table);
            return;
        }
        SixMaxRankTextureStudy.Report report;
        if (solve) {
            var result =
                    SixMaxRankTextureStudy.solve(
                            source,
                            table,
                            SixMaxRankTextureStudy.select(source, ranks, fraction),
                            iterations,
                            pruning);
            SixMaxRankTextureStudy.write(paths.get(2), result.checkpoint(), source, table);
            report = result.report();
            System.out.println(
                    "TRAVERSAL "
                            + result.traversal()
                            + " pruned suffix roots "
                            + result.inactiveUtilityPrunedNodes());
        } else
            report =
                    SixMaxRankTextureStudy.assess(
                            source,
                            table,
                            SixMaxRankTextureStudy.read(paths.get(2), source, table));
        SixMaxTexturePayoffTableMain.atomicWrite(paths.get(3), SixMaxTextureStudy.json(report));
        System.out.println(
                "VALIDATION_ONLY rank/texture NashConv bb "
                        + report.jointlySolvedInRankTextureGame().nashConvBb());
    }
}
