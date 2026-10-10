package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Offline owned diagnostic with new output paths and strict physical replay. */
public final class SixMaxSuppliedRootActionIntervalsMain {
    private SixMaxSuppliedRootActionIntervalsMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <request.json[.gz]> <report.json[.gz]>");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        if (args[0].equals("solve") && Files.exists(report))
            throw new IllegalArgumentException("Root interval output must be a new path");
        var result =
                args[0].equals("solve")
                        ? SixMaxSuppliedRootActionIntervals.solve(
                                SixMaxSuppliedRootActionIntervals.readRequest(request))
                        : SixMaxSuppliedRootActionIntervals.replay(request, report);
        if (args[0].equals("solve")) SixMaxSuppliedRootActionIntervals.write(report, result);
        System.out.println(
                (args[0].equals("solve") ? "SOLVED" : "REPLAYED")
                        + " root action EV interval ["
                        + result.report().lowerEvBb()
                        + ", "
                        + result.report().upperEvBb()
                        + "] bb securitySlack="
                        + result.report().request().securitySlack()
                        + " trainerAdmission=false report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
