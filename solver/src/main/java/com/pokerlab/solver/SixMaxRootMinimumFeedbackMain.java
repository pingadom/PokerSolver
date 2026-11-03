package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxRootMinimumFeedbackMain {
    private SixMaxRootMinimumFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "solve|replay request original-envelope original-minimum report");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var envelope = Path.of(args[2]).toAbsolutePath().normalize();
        var minimum = Path.of(args[3]).toAbsolutePath().normalize();
        var output = Path.of(args[4]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, envelope, minimum, output));
        if (args[0].equals("solve") && Files.exists(output))
            throw new IllegalArgumentException("Output already exists");
        var owned =
                args[0].equals("solve")
                        ? SixMaxRootMinimumFeedback.solve(
                                SixMaxRootMinimumFeedback.readRequest(input), envelope, minimum)
                        : SixMaxRootMinimumFeedback.replay(input, envelope, minimum, output);
        if (args[0].equals("solve")) SixMaxRootMinimumFeedback.write(output, owned);
        System.out.println(
                SixMaxRootMinimumFeedback.STATUS
                        + " qualified="
                        + owned.report().qualifiedForOfflineDrill());
    }
}
