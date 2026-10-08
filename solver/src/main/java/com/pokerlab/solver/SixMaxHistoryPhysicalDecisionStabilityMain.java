package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Exact full-lineage replay followed by bounded fresh reference solves. */
public final class SixMaxHistoryPhysicalDecisionStabilityMain {
    private SixMaxHistoryPhysicalDecisionStabilityMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 9 || !(args[0].equals("screen") || args[0].equals("replay")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalDecisionStabilityMain <screen|replay> <source> <rank-table> <physical-table> <checkpoint> <study-report> <derived-policy> <derived-report> <screen-report>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        boolean screen = args[0].equals("screen");
        if (screen && Files.exists(paths.get(7)))
            throw new IllegalArgumentException("Decision screen output must be a new path");
        var validated = SixMaxHistoryPhysicalConditionalRefinementMain.predecessor(paths);
        var derived =
                SixMaxHistoryPhysicalConditionalRefinement.replay(
                        paths.get(5), paths.get(6), validated);
        var report =
                screen
                        ? SixMaxHistoryPhysicalDecisionStability.screen(
                                derived,
                                SixMaxSuitDecisionStability.Settings.standard(),
                                b ->
                                        System.out.println(
                                                b.observationKey()
                                                        + " "
                                                        + (b.retained() ? "STABLE" : b.failures())))
                        : SixMaxHistoryPhysicalDecisionStability.replay(paths.get(7), derived);
        if (screen) SixMaxHistoryPhysicalDecisionStability.write(paths.get(7), report);
        System.out.println(
                "VALIDATION_ONLY trainerAdmission=false screened="
                        + report.branches().size()
                        + " retained="
                        + report.branches().stream()
                                .filter(SixMaxSuitDecisionStability.Branch::retained)
                                .count()
                        + " retainedAllHeadsUpFraction="
                        + report.retainedAllHeadsUpFraction());
    }
}
