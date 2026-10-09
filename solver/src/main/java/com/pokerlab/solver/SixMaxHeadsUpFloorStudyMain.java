package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/**
 * Offline floor diagnostics; no policy export or trainer registration. Outputs are bounded JSON or
 * gzip.
 */
public final class SixMaxHeadsUpFloorStudyMain {
    private SixMaxHeadsUpFloorStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (!(args.length == 5 && args[0].equals("solve")
                || args.length == 3 && args[0].equals("replay")))
            throw new IllegalArgumentException(
                    "Usage: solve <source.json> <specification.json> <floor> <new-report.json[.gz]> | replay <source.json> <report.json[.gz]>");
        boolean solve = args[0].equals("solve");
        var sourcePath = Path.of(args[1]).toAbsolutePath().normalize();
        var reportPath = Path.of(args[args.length - 1]).toAbsolutePath().normalize();
        var paths = new ArrayList<>(List.of(sourcePath, reportPath));
        if (solve) paths.add(Path.of(args[2]).toAbsolutePath().normalize());
        SixMaxTexturePayoffTableMain.distinct(paths);
        if (solve && Files.exists(reportPath))
            throw new IllegalArgumentException("Floor output must be new");
        double floor = solve ? Double.parseDouble(args[3]) : 0;
        if (solve) FiniteTwoPlayerBehaviorFloor.requireFloor(floor);
        var source = SixMaxTexturePayoffTableMain.source(sourcePath);
        var result =
                solve
                        ? SixMaxHeadsUpFloorStudy.solve(
                                source,
                                SixMaxTexturePayoffTable.mapper()
                                        .readValue(
                                                SixMaxRankTexturePayoffTable.readBytes(
                                                        paths.getLast(),
                                                        SixMaxHeadsUpPreflopStudy.MAX_BYTES),
                                                SixMaxHeadsUpPreflopGame.Specification.class),
                                floor)
                        : SixMaxHeadsUpFloorStudy.replay(reportPath, source);
        if (solve) SixMaxHeadsUpFloorStudy.write(reportPath, result);
        System.out.println(
                (solve ? "SOLVED" : "REPLAYED")
                        + " floor diagnostic "
                        + SixMaxHeadsUpPreflopGame.hash(result.report())
                        + " stable="
                        + result.report().stableDecisions()
                        + "/"
                        + result.report().materialDecisions()
                        + " trainerAdmission=false");
    }
}
