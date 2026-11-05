package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxRootSequenceFeedbackMain {
    private SixMaxRootSequenceFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "solve|replay request original-envelope legacy-minimum sequence-minimum report");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var envelope = Path.of(args[2]).toAbsolutePath().normalize();
        var legacy = Path.of(args[3]).toAbsolutePath().normalize();
        var minimum = Path.of(args[4]).toAbsolutePath().normalize();
        var output = Path.of(args[5]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, envelope, legacy, minimum, output));
        if (args[0].equals("solve") && Files.exists(output))
            throw new IllegalArgumentException("Output already exists");
        var owned =
                args[0].equals("solve")
                        ? SixMaxRootSequenceFeedback.solve(
                                SixMaxRootSequenceFeedback.readRequest(input),
                                envelope,
                                legacy,
                                minimum)
                        : SixMaxRootSequenceFeedback.replay(
                                input, envelope, legacy, minimum, output);
        if (args[0].equals("solve")) SixMaxRootSequenceFeedback.write(output, owned);
        System.out.println(
                SixMaxRootSequenceFeedback.STATUS
                        + " qualified="
                        + owned.report().qualifiedForOfflineDrill());
    }
}
