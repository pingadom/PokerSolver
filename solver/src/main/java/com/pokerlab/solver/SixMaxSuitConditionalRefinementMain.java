package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Offline, bounded targeted optimization; no trainer admission or joint checkpoint relabelling. */
public final class SixMaxSuitConditionalRefinementMain {
    private SixMaxSuitConditionalRefinementMain() {}

    public static void main(String[] args) throws Exception {
        boolean refine = args.length == 11 && args[0].equals("refine");
        boolean replay = args.length == 7 && args[0].equals("replay");
        if (!refine && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxSuitConditionalRefinementMain <refine|replay> <source> <rank-table> <suit-table> <predecessor> <derived-policy> <report> [<LARGEST_GAP|REACH_WEIGHTED_GAP|BALANCED_GAP_AND_REACH> <maximum-cases> <fresh-budgets-csv> <target-gap-bb>]");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(6)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var settings =
                refine
                        ? new SixMaxSuitConditionalRefinement.Settings(
                                SixMaxSuitConditionalRefinement.Priority.valueOf(args[7]),
                                Integer.parseInt(args[8]),
                                Arrays.stream(args[9].split(",", -1))
                                        .map(Integer::parseInt)
                                        .toList(),
                                Double.parseDouble(args[10]))
                        : null;
        if (refine && Files.exists(paths.get(4)))
            throw new IllegalArgumentException("Derived policy output must be a new path");
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var table = SixMaxSuitRefinementPayoffTable.read(paths.get(2), source, parent);
        var cp = SixMaxSuitRefinementStudy.read(paths.get(3), source, parent, table);
        var result =
                replay
                        ? SixMaxSuitConditionalRefinement.replay(
                                paths.get(4), paths.get(5), source, parent, table, cp)
                        : SixMaxSuitConditionalRefinement.refine(
                                source,
                                parent,
                                table,
                                cp,
                                settings,
                                branch ->
                                        System.out.println(
                                                "LOCAL "
                                                        + branch.observationKey()
                                                        + " "
                                                        + branch.status()
                                                        + " gap "
                                                        + branch.before().nashConvBb()
                                                        + " -> "
                                                        + branch.after().nashConvBb()));
        if (refine) SixMaxSuitConditionalRefinement.write(paths.get(4), paths.get(5), result);
        var report = result.report();
        System.out.println(
                "VALIDATION_ONLY "
                        + (report.accepted() ? "ACCEPTED" : "REJECTED " + report.rejectionReasons())
                        + " parent gap "
                        + report.before().parentWitness().parentQuality().nashConvBb()
                        + " -> "
                        + report.after().parentWitness().parentQuality().nashConvBb()
                        + " maximum local gap "
                        + report.before().summary().largestConditionalGapBb()
                        + " -> "
                        + report.after().summary().largestConditionalGapBb());
    }
}
