package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSuppliedReachRejectionMain {
    private SixMaxSuppliedReachRejectionMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("diagnose", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: diagnose|replay <request.json[.gz]> <report.json[.gz]>");
        var request = Path.of(args[1]).toAbsolutePath().normalize();
        var report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        boolean create = args[0].equals("diagnose");
        if (create && Files.exists(report))
            throw new IllegalArgumentException("Reach rejection output must be a new path");
        var result =
                create
                        ? SixMaxSuppliedReachRejection.diagnose(
                                SixMaxSuppliedReachRejection.readRequest(request))
                        : SixMaxSuppliedReachRejection.replay(request, report);
        if (create) SixMaxSuppliedReachRejection.write(report, result);
        var diagnostic = result.report().diagnostic();
        System.out.println(
                "trainerAdmission=false status="
                        + diagnostic.status()
                        + " certifiedReachLower="
                        + diagnostic.certifiedReachLowerBound()
                        + " witnessReach="
                        + diagnostic.minimumReachWitness().questionReach()
                        + " requiredReach="
                        + diagnostic.requiredMinimumReach()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
