package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/**
 * Offline BB discovery CLI. Inspection reads an unverified snapshot; replay regenerates all cases.
 */
public final class SixMaxBigBlindFlopCoverageStudyMain {
    private SixMaxBigBlindFlopCoverageStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("inspect")) {
            System.out.println(
                    summary(SixMaxBigBlindFlopCoverageStudy.readReport(Path.of(args[1])), true));
            return;
        }
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <BB-request> <new-report.json[.gz]> | inspect <report>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var result =
                create
                        ? SixMaxBigBlindFlopCoverageStudy.solve(
                                SixMaxBigBlindFlopCoverageStudy.readRequest(input))
                        : SixMaxBigBlindFlopCoverageStudy.replay(input, output);
        if (create) SixMaxBigBlindFlopCoverageStudy.write(output, result);
        System.out.println(summary(result.report(), false));
        System.out.println(
                "trainerAdmission=false freshReferenceRuns=0 counts="
                        + result.report().counts()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }

    static String summary(SixMaxBigBlindFlopCoverageStudy.Report report, boolean snapshotOnly) {
        var text =
                new StringBuilder(
                        "storedSnapshotOnly="
                                + snapshotOnly
                                + " trainerAdmission=false freshReferenceRuns=0\n");
        for (var item : report.cases()) {
            text.append(item.index())
                    .append(" BB ")
                    .append(item.request().heroCards())
                    .append(
                            item.request().flopActions().isEmpty()
                                    ? " first to act"
                                    : " after BB check / BTN bet")
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
