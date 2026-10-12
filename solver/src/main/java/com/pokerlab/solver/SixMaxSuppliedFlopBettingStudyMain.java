package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSuppliedFlopBettingStudyMain {
    private SixMaxSuppliedFlopBettingStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <request.json[.gz]> <report.json[.gz]>");
        var request = Path.of(args[1]).toAbsolutePath().normalize();
        var report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(report))
            throw new IllegalArgumentException("Connected output must be new");
        var result =
                create
                        ? SixMaxSuppliedFlopBettingStudy.solve(
                                SixMaxSuppliedFlopBettingStudy.readRequest(request))
                        : SixMaxSuppliedFlopBettingStudy.replay(request, report);
        if (create) SixMaxSuppliedFlopBettingStudy.write(report, result);
        System.out.println(
                "trainerAdmission=false model="
                        + result.report().binding().model()
                        + " states="
                        + result.report().solve().treeNodes()
                        + " nashConvBb="
                        + result.report().solve().behavioralQuality().nashConvBb()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
