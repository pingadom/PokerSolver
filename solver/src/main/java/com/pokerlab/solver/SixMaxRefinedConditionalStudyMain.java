package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxRefinedConditionalStudyMain {
    private SixMaxRefinedConditionalStudyMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <refined-conditional-request> <new-report.json[.gz]>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var result =
                create
                        ? SixMaxRefinedConditionalStudy.solve(
                                SixMaxRefinedConditionalStudy.readRequest(input))
                        : SixMaxRefinedConditionalStudy.replay(input, output);
        if (create) SixMaxRefinedConditionalStudy.write(output, result);
        System.out.println(
                "trainerAdmission=false conditionalResearchOnly=true actions="
                        + result.report().actions().size()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(result.report()));
    }
}
