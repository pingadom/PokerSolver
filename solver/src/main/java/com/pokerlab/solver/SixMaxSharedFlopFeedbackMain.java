package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSharedFlopFeedbackMain {
    private SixMaxSharedFlopFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <shared-request> <new-report.json[.gz]>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var owned =
                create
                        ? SixMaxSharedFlopFeedback.solve(
                                SixMaxSharedFlopFeedback.readRequest(input))
                        : SixMaxSharedFlopFeedback.replay(input, output);
        if (create) SixMaxSharedFlopFeedback.write(output, owned);
        System.out.println(
                "trainerAdmission=false qualified="
                        + owned.report().qualifiedForOfflineDrill()
                        + " questions="
                        + owned.report().cases().size()
                        + " freshReferenceRuns=2 report="
                        + SixMaxHeadsUpPreflopGame.hash(owned.report()));
    }
}
