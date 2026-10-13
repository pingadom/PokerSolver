package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxLaterFlopFeedbackMain {
    private SixMaxLaterFlopFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <later-feedback-request> <later-feedback-report>");
        var request = Path.of(args[1]).toAbsolutePath().normalize();
        var report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(report))
            throw new IllegalArgumentException("Output must be new");
        var result =
                create
                        ? SixMaxLaterFlopFeedback.solve(
                                SixMaxLaterFlopFeedback.readRequest(request))
                        : SixMaxLaterFlopFeedback.replay(request, report);
        if (create) SixMaxLaterFlopFeedback.write(report, result);
        System.out.println(
                "trainerAdmission=false qualified="
                        + result.report().qualifiedForOfflineDrill()
                        + " moves="
                        + result.report().moves().size()
                        + " rejections="
                        + result.report().rejectionReasons()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
