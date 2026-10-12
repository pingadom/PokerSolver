package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSuppliedReachBoundsMain {
    private SixMaxSuppliedReachBoundsMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("assess", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: assess|replay <request.json[.gz]> <report.json[.gz]>");
        var request = Path.of(args[1]).toAbsolutePath().normalize();
        var report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        boolean create = args[0].equals("assess");
        if (create && Files.exists(report))
            throw new IllegalArgumentException("Reach bounds output must be a new path");
        var result =
                create
                        ? SixMaxSuppliedReachBounds.assess(
                                SixMaxSuppliedReachBounds.readRequest(request))
                        : SixMaxSuppliedReachBounds.replay(request, report);
        if (create) SixMaxSuppliedReachBounds.write(report, result);
        var diagnostic = result.report().diagnostic();
        System.out.println(
                "trainerAdmission=false status="
                        + diagnostic.status()
                        + " certifiedReachLower="
                        + diagnostic.certifiedReachLowerBound()
                        + " certifiedReachUpper="
                        + diagnostic.certifiedReachUpperBound()
                        + " minimumWitnessReach="
                        + diagnostic.minimumReachWitness().questionReach()
                        + " maximumWitnessReach="
                        + diagnostic.maximumReachWitness().questionReach()
                        + " requiredReach="
                        + diagnostic.requiredMinimumReach()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
