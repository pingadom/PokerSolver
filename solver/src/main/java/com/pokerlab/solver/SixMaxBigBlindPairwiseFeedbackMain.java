package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxBigBlindPairwiseFeedbackMain {
    private SixMaxBigBlindPairwiseFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("inspect")) {
            var saved = SixMaxBigBlindPairwiseFeedback.readReport(Path.of(args[1]));
            System.out.println(
                    "storedSnapshotOnly=true trainerAdmission=false qualified="
                            + saved.qualifiedIndices());
            return;
        }
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <BB-feedback-request> <new-report.json[.gz]> | inspect <report>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var owned =
                create
                        ? SixMaxBigBlindPairwiseFeedback.solve(
                                SixMaxBigBlindPairwiseFeedback.readRequest(input))
                        : SixMaxBigBlindPairwiseFeedback.replay(input, output);
        if (create) SixMaxBigBlindPairwiseFeedback.write(output, owned);
        for (var item : owned.report().cases())
            System.out.println(
                    item.index()
                            + " qualified="
                            + item.qualifiedForOfflineDrill()
                            + " reasons="
                            + item.rejectionReasons());
        System.out.println(
                "storedSnapshotOnly=false trainerAdmission=false freshReferenceRuns=2 qualified="
                        + owned.report().qualifiedIndices()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(owned.report()));
    }
}
