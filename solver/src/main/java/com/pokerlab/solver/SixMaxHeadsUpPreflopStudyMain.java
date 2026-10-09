package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Offline solve/replay; does not register research content with the public trainer API. */
public final class SixMaxHeadsUpPreflopStudyMain {
    private SixMaxHeadsUpPreflopStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1
                || !(args[0].equals("solve") && args.length == 5
                        || args[0].equals("replay") && args.length == 4))
            throw new IllegalArgumentException(
                    "Usage: solve <source.json> <specification.json> <policy.json[.gz]> <report.json[.gz]> | replay <source.json> <policy.json[.gz]> <report.json[.gz]>");
        var paths = new ArrayList<Path>();
        for (int i = 1; i < args.length; i++)
            paths.add(Path.of(args[i]).toAbsolutePath().normalize());
        SixMaxTexturePayoffTableMain.distinct(paths);
        boolean solve = args[0].equals("solve");
        var policy = paths.get(paths.size() - 2);
        var report = paths.getLast();
        if (solve && (Files.exists(policy) || Files.exists(report)))
            throw new IllegalArgumentException("Conditional outputs must be new paths");
        SixMaxHeadsUpPreflopGame.Specification specification = null;
        if (solve)
            specification =
                    SixMaxTexturePayoffTable.mapper()
                            .readValue(
                                    SixMaxRankTexturePayoffTable.readBytes(
                                            paths.get(1), SixMaxHeadsUpPreflopStudy.MAX_BYTES),
                                    SixMaxHeadsUpPreflopGame.Specification.class);
        var source = SixMaxTexturePayoffTableMain.source(paths.getFirst());
        var result =
                solve
                        ? SixMaxHeadsUpPreflopStudy.solve(source, specification)
                        : SixMaxHeadsUpPreflopStudy.replay(policy, report, source);
        if (solve) SixMaxHeadsUpPreflopStudy.write(policy, report, result);
        System.out.println(
                (solve ? "SOLVED" : "REPLAYED")
                        + " conditional preflop "
                        + SixMaxHeadsUpPreflopGame.hash(result.report())
                        + " accepted="
                        + result.report().accepted()
                        + " stable="
                        + result.report().stableDecisions()
                        + "/"
                        + result.report().materialDecisions()
                        + " trainerAdmission=false");
    }
}
