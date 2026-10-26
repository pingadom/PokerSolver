package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxBigBlindPairwiseStudyMain {
    private SixMaxBigBlindPairwiseStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("inspect")) {
            var saved = SixMaxBigBlindPairwiseStudy.readReport(Path.of(args[1]));
            System.out.println(
                    "storedSnapshotOnly=true trainerAdmission=false candidates="
                            + saved.candidateIndices());
            return;
        }
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <BB-v2-request> <new-report.json[.gz]> | inspect <report>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var owned =
                create
                        ? SixMaxBigBlindPairwiseStudy.solve(
                                SixMaxBigBlindFlopCoverageStudy.readRequest(input))
                        : SixMaxBigBlindPairwiseStudy.replay(input, output);
        if (create) SixMaxBigBlindPairwiseStudy.write(output, owned);
        System.out.println(
                "storedSnapshotOnly=false trainerAdmission=false freshReferenceRuns=0 candidates="
                        + owned.report().candidateIndices()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(owned.report()));
    }
}
