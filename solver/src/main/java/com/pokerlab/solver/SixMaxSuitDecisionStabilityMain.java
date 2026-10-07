package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Offline physical-decision research; validates its full derived-policy lineage before screening.
 */
public final class SixMaxSuitDecisionStabilityMain {
    private SixMaxSuitDecisionStabilityMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 8 || !(args[0].equals("screen") || args[0].equals("replay")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxSuitDecisionStabilityMain <screen|replay> <source> <rank-table> <suit-table> <predecessor> <derived-policy> <derived-report> <screen-report>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        boolean screen = args[0].equals("screen");
        if (screen && Files.exists(paths.get(6)))
            throw new IllegalArgumentException("Screen output must be a new path");
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var table = SixMaxSuitRefinementPayoffTable.read(paths.get(2), source, parent);
        var cp = SixMaxSuitRefinementStudy.read(paths.get(3), source, parent, table);
        var derived =
                SixMaxSuitConditionalRefinement.replay(
                        paths.get(4), paths.get(5), source, parent, table, cp);
        var report =
                screen
                        ? SixMaxSuitDecisionStability.screen(
                                source,
                                parent,
                                table,
                                cp,
                                derived,
                                SixMaxSuitDecisionStability.Settings.standard(),
                                b ->
                                        System.out.println(
                                                b.observationKey()
                                                        + " "
                                                        + (b.retained() ? "STABLE" : b.failures())))
                        : SixMaxSuitDecisionStability.replay(
                                paths.get(6), source, parent, table, cp, derived);
        if (screen) SixMaxSuitDecisionStability.write(paths.get(6), report);
        System.out.println(
                "VALIDATION_ONLY trainerAdmission=false screened="
                        + report.branches().size()
                        + " retained="
                        + report.branches().stream()
                                .filter(SixMaxSuitDecisionStability.Branch::retained)
                                .count()
                        + " retainedPhysicalReach="
                        + report.retainedPhysicalReach());
    }
}
