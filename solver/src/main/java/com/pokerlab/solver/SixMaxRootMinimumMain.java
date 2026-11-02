package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxRootMinimumMain {
    private SixMaxRootMinimumMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException("solve|replay request pinned-source report");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                source = Path.of(args[2]).toAbsolutePath().normalize(),
                output = Path.of(args[3]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, source, output));
        if (args[0].equals("solve") && Files.exists(output))
            throw new IllegalArgumentException("Output already exists");
        var result =
                args[0].equals("solve")
                        ? SixMaxRootMinimumStudy.solve(
                                SixMaxRootMinimumStudy.readRequest(request), source)
                        : SixMaxRootMinimumStudy.replay(request, source, output);
        if (args[0].equals("solve")) SixMaxRootMinimumStudy.write(output, result);
        System.out.println(
                SixMaxRootMinimumStudy.STATUS
                        + " completeMinimum="
                        + result.report().completeMinimum()
                        + " referenceSupported="
                        + result.report().referenceSupportedMinimum());
    }
}
