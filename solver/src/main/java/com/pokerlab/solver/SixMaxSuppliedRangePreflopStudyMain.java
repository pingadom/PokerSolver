package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Offline only. Source-policy-derived studies retain their separate command and identity. */
public final class SixMaxSuppliedRangePreflopStudyMain {
    private SixMaxSuppliedRangePreflopStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2 && args.length != 4
                || args.length == 2 && !args[0].equals("preflight")
                || args.length == 4 && !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: preflight <input.json> | solve|replay <input.json> <policy.json[.gz]> <report.json[.gz]>");
        var paths = new ArrayList<Path>();
        for (int i = 1; i < args.length; i++)
            paths.add(Path.of(args[i]).toAbsolutePath().normalize());
        SixMaxTexturePayoffTableMain.distinct(paths);
        if (args[0].equals("solve") && (Files.exists(paths.get(1)) || Files.exists(paths.get(2))))
            throw new IllegalArgumentException("Supplied study outputs must be new paths");
        var input =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        paths.getFirst(),
                                        SixMaxSuppliedRangePreflopStudy.MAX_BYTES),
                                SixMaxSuppliedRangePreflopGame.Input.class);
        var budget = SixMaxSuppliedRangePreflopGame.preflight(input);
        if (args[0].equals("preflight")) {
            System.out.println(SixMaxTextureStudy.json(budget));
            return;
        }
        var result =
                args[0].equals("solve")
                        ? SixMaxSuppliedRangePreflopStudy.solve(input)
                        : SixMaxSuppliedRangePreflopStudy.replay(
                                paths.get(0), paths.get(1), paths.get(2));
        if (args[0].equals("solve"))
            SixMaxSuppliedRangePreflopStudy.write(paths.get(1), paths.get(2), result);
        System.out.println(
                (args[0].equals("solve") ? "SOLVED" : "REPLAYED")
                        + " supplied joint preflop "
                        + SixMaxHeadsUpPreflopGame.hash(result.report())
                        + " qualifiedForOfflinePractice="
                        + result.report().qualifiedForOfflinePractice()
                        + " stable="
                        + result.report().stableDecisions()
                        + "/"
                        + result.report().materialDecisions()
                        + " sourceHistoryReach=UNAVAILABLE trainerAdmission=false");
    }
}
