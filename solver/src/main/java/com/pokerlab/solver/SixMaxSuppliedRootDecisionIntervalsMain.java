package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSuppliedRootDecisionIntervalsMain {
    private SixMaxSuppliedRootDecisionIntervalsMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <request.json[.gz]> <report.json[.gz]>");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        if (args[0].equals("solve") && Files.exists(report))
            throw new IllegalArgumentException("Root decision output must be a new path");
        var result =
                args[0].equals("solve")
                        ? SixMaxSuppliedRootDecisionIntervals.solve(
                                SixMaxSuppliedRootDecisionIntervals.readRequest(request))
                        : SixMaxSuppliedRootDecisionIntervals.replay(request, report);
        if (args[0].equals("solve")) SixMaxSuppliedRootDecisionIntervals.write(report, result);
        System.out.println(
                SixMaxTextureStudy.json(
                        result.report().moves().stream()
                                .map(
                                        m ->
                                                Map.of(
                                                        "action",
                                                        m.action(),
                                                        "lowerEvBb",
                                                        m.lowerEvBb(),
                                                        "upperEvBb",
                                                        m.upperEvBb(),
                                                        "lowerRegretBb",
                                                        m.lowerRegretBb(),
                                                        "upperRegretBb",
                                                        m.upperRegretBb(),
                                                        "robustlyBestAtCertificateTolerance",
                                                        m.robustlyBestAtCertificateTolerance()))
                                .toList()));
        System.out.println(
                "trainerAdmission=false report=" + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
