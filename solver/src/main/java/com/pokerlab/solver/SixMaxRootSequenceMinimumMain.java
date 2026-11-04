package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxRootSequenceMinimumMain {
    private SixMaxRootSequenceMinimumMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "solve|replay request pinned-source pinned-legacy-minimum report");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                source = Path.of(args[2]).toAbsolutePath().normalize(),
                legacy = Path.of(args[3]).toAbsolutePath().normalize(),
                output = Path.of(args[4]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, source, legacy, output));
        if (args[0].equals("solve") && Files.exists(output))
            throw new IllegalArgumentException("Output already exists");
        var result =
                args[0].equals("solve")
                        ? SixMaxRootSequenceMinimumStudy.solve(
                                SixMaxRootSequenceMinimumStudy.readRequest(request), source, legacy)
                        : SixMaxRootSequenceMinimumStudy.replay(request, source, legacy, output);
        if (args[0].equals("solve")) SixMaxRootSequenceMinimumStudy.write(output, result);
        System.out.println(
                SixMaxRootSequenceMinimumStudy.STATUS
                        + " completeMinimum="
                        + result.report().completeMinimum()
                        + " referenceSupported="
                        + result.report().referenceSupportedMinimum());
    }
}
