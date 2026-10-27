package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxBigBlindPrimaryScreenMain {
    private SixMaxBigBlindPrimaryScreenMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("inspect")) {
            var saved = SixMaxBigBlindPrimaryScreen.readReport(Path.of(args[1]));
            System.out.println(
                    "storedSnapshotOnly=true trainerAdmission=false primaryEligible="
                            + saved.primaryEligibleIndices());
            return;
        }
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <BB-v2-request> <new-screen.json[.gz]> | inspect <screen>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var owned =
                create
                        ? SixMaxBigBlindPrimaryScreen.solve(
                                SixMaxBigBlindFlopCoverageStudy.readRequest(input))
                        : SixMaxBigBlindPrimaryScreen.replay(input, output);
        if (create) SixMaxBigBlindPrimaryScreen.write(output, owned);
        for (var item : owned.report().cases())
            System.out.println(
                    item.index()
                            + " primaryEligible="
                            + item.primaryEligible()
                            + " reasons="
                            + item.rejectionReasons());
        System.out.println(
                "storedSnapshotOnly=false trainerAdmission=false freshReferenceRuns=0 primaryEligible="
                        + owned.report().primaryEligibleIndices()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(owned.report()));
    }
}
