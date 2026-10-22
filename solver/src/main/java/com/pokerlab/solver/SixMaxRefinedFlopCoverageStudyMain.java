package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/**
 * Offline discovery only. No request handler runs a survey and no report creates grading authority.
 */
public final class SixMaxRefinedFlopCoverageStudyMain {
    private SixMaxRefinedFlopCoverageStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("inspect")) {
            System.out.println(
                    summary(SixMaxRefinedFlopCoverageStudy.readReport(Path.of(args[1])), true));
            return;
        }
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <coverage-request.json> <new-report.json[.gz]> | inspect <report>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var result =
                create
                        ? SixMaxRefinedFlopCoverageStudy.solve(
                                SixMaxRefinedFlopCoverageStudy.readRequest(input))
                        : SixMaxRefinedFlopCoverageStudy.replay(input, output);
        if (create) SixMaxRefinedFlopCoverageStudy.write(output, result);
        System.out.println(summary(result.report(), false));
        System.out.println(
                "trainerAdmission=false freshReferenceRuns=0 counts="
                        + result.report().counts()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }

    static String summary(SixMaxRefinedFlopCoverageStudy.Report report, boolean snapshotOnly) {
        var text =
                new StringBuilder(
                        "storedSnapshotOnly="
                                + snapshotOnly
                                + " trainerAdmission=false freshReferenceRuns=0\n");
        for (var item : report.cases()) {
            text.append(item.index())
                    .append(" BTN ")
                    .append(item.request().heroCards())
                    .append(" after BB ")
                    .append(item.request().flopActions().getFirst())
                    .append(" : ")
                    .append(item.outcome());
            if (item.rejection() != null)
                text.append(" ")
                        .append(item.rejection().reason())
                        .append(": ")
                        .append(item.rejection().detail());
            for (var move : item.moves())
                text.append(" ").append(move.action()).append("=").append(move.classification());
            text.append('\n');
        }
        return text.toString();
    }
}
