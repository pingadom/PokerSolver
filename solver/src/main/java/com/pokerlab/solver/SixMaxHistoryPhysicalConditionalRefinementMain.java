package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Offline frozen-preflop refinement with complete payoff and predecessor report replay. */
public final class SixMaxHistoryPhysicalConditionalRefinementMain {
    private SixMaxHistoryPhysicalConditionalRefinementMain() {}

    public static void main(String[] args) throws Exception {
        boolean refine = args.length == 12 && args[0].equals("refine");
        boolean replay = args.length == 8 && args[0].equals("replay");
        if (!refine && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalConditionalRefinementMain <refine|replay> <source> <rank-table> <physical-table> <checkpoint> <study-report> <derived-policy> <derived-report> [<maximum-cases> <fresh-budgets-csv> <target-gap-bb> <ALL_MATERIAL|PRIMARY_ACCURATE>]");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .limit(7)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var settings =
                refine
                        ? new SixMaxSuitConditionalRefinement.Settings(
                                SixMaxSuitConditionalRefinement.Priority.BALANCED_GAP_AND_REACH,
                                Integer.parseInt(args[8]),
                                Arrays.stream(args[9].split(",", -1))
                                        .map(Integer::parseInt)
                                        .toList(),
                                Double.parseDouble(args[10]))
                        : null;
        if (refine && (Files.exists(paths.get(5)) || Files.exists(paths.get(6))))
            throw new IllegalArgumentException("Physical derivative outputs must be new paths");
        if (refine && !(args[11].equals("ALL_MATERIAL") || args[11].equals("PRIMARY_ACCURATE")))
            throw new IllegalArgumentException("Unknown physical refinement selection");
        var validated = predecessor(paths);
        var result =
                replay
                        ? SixMaxHistoryPhysicalConditionalRefinement.replay(
                                paths.get(5), paths.get(6), validated)
                        : SixMaxHistoryPhysicalConditionalRefinement.refine(
                                validated,
                                settings,
                                args[11].equals("PRIMARY_ACCURATE"),
                                b ->
                                        System.out.println(
                                                "LOCAL "
                                                        + b.observationKey()
                                                        + " "
                                                        + b.status()
                                                        + " gap "
                                                        + b.before().nashConvBb()
                                                        + " -> "
                                                        + b.after().nashConvBb()));
        if (refine)
            SixMaxHistoryPhysicalConditionalRefinement.write(paths.get(5), paths.get(6), result);
        var report = result.report();
        System.out.println(
                "VALIDATION_ONLY trainerAdmission=false "
                        + (report.accepted() ? "ACCEPTED" : "REJECTED " + report.rejectionReasons())
                        + " replaced="
                        + report.replacedInformationSets()
                        + " preserved="
                        + report.preservedInformationSets()
                        + " parent="
                        + report.before().parentWitness().parentQuality().nashConvBb()
                        + " -> "
                        + report.after().parentWitness().parentQuality().nashConvBb());
    }

    static SixMaxHistoryPhysicalStudy.Validated predecessor(List<Path> paths) throws Exception {
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var table = SixMaxHistoryPhysicalPayoffTable.replay(paths.get(2), source, parent);
        var cp = SixMaxHistoryPhysicalStudy.read(paths.get(3), source, parent, table);
        return SixMaxHistoryPhysicalStudy.replayValidated(paths.get(4), source, parent, table, cp);
    }
}
