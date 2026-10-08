package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Exact full-lineage replay followed by bounded fresh reference solves. */
public final class SixMaxHistoryPhysicalMaxminDecisionStabilityMain {
    private SixMaxHistoryPhysicalMaxminDecisionStabilityMain() {}

    public static void main(String[] args) throws Exception {
        boolean derivedInput = args.length > 0 && args[0].endsWith("-derived");
        if (args.length != (derivedInput ? 11 : 9)
                || !(args[0].equals(derivedInput ? "screen-derived" : "screen")
                        || args[0].equals(derivedInput ? "replay-derived" : "replay")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalMaxminDecisionStabilityMain <screen|replay|screen-derived|replay-derived> <source> <rank-table> <physical-table> <checkpoint> <study-report> [<cfr-policy> <cfr-report> for -derived] <maxmin-policy> <maxmin-report> <screen-report>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        boolean screen = args[0].startsWith("screen");
        int reportIndex = derivedInput ? 9 : 7;
        if (screen && Files.exists(paths.get(reportIndex)))
            throw new IllegalArgumentException("Decision screen output must be a new path");
        var derived = SixMaxHistoryPhysicalMaxminMain.replay(paths, derivedInput);
        var report =
                screen
                        ? SixMaxHistoryPhysicalMaxminDecisionStability.screen(
                                derived,
                                SixMaxSuitDecisionStability.Settings.standard(),
                                b ->
                                        System.out.println(
                                                b.observationKey()
                                                        + " "
                                                        + (b.retained() ? "STABLE" : b.failures())))
                        : SixMaxHistoryPhysicalMaxminDecisionStability.replay(
                                paths.get(reportIndex), derived);
        if (screen)
            SixMaxHistoryPhysicalMaxminDecisionStability.write(paths.get(reportIndex), report);
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
