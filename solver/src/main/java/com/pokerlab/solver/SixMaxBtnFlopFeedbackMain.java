package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxBtnFlopFeedbackMain {
    private SixMaxBtnFlopFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <btn-feedback-request> <btn-feedback-report>");
        var request = Path.of(args[1]).toAbsolutePath().normalize();
        var report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(report))
            throw new IllegalArgumentException("Output must be new");
        System.out.println(
                "Offline BTN budget: fresh CFR+ 80000 + 160000 iterations; HTTP must not call this CLI.");
        var result =
                create
                        ? SixMaxBtnFlopFeedback.solve(SixMaxBtnFlopFeedback.readRequest(request))
                        : SixMaxBtnFlopFeedback.replay(request, report);
        if (create) SixMaxBtnFlopFeedback.write(report, result);
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
