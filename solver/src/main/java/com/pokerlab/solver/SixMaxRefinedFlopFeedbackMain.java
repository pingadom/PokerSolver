package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxRefinedFlopFeedbackMain {
    private SixMaxRefinedFlopFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <refined-feedback-request> <new-report.json[.gz]>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var result =
                create
                        ? SixMaxRefinedFlopFeedback.solve(
                                SixMaxRefinedFlopFeedback.readRequest(input))
                        : SixMaxRefinedFlopFeedback.replay(input, output);
        if (create) SixMaxRefinedFlopFeedback.write(output, result);
        System.out.println(
                "trainerAdmission=false qualified="
                        + result.report().qualifiedForOfflineDrill()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
