package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSuppliedConditionalDecisionLossMain {
    private SixMaxSuppliedConditionalDecisionLossMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <request.json[.gz]> <report.json[.gz]>");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        if (args[0].equals("solve") && Files.exists(report))
            throw new IllegalArgumentException("Joint loss output must be a new path");
        var result =
                args[0].equals("solve")
                        ? SixMaxSuppliedConditionalDecisionLoss.solve(
                                SixMaxSuppliedConditionalDecisionLoss.readRequest(request))
                        : SixMaxSuppliedConditionalDecisionLoss.replay(request, report);
        if (args[0].equals("solve")) SixMaxSuppliedConditionalDecisionLoss.write(report, result);
        System.out.println(SixMaxTextureStudy.json(result.report().moves()));
        System.out.println(
                "trainerAdmission=false report=" + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
