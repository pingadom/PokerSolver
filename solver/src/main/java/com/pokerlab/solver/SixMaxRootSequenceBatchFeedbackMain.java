package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxRootSequenceBatchFeedbackMain {
    private SixMaxRootSequenceBatchFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 7 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "solve|replay request original-envelope prior-minimum physical-batch previous-feedback output.gz");
        var paths =
                java.util.Arrays.stream(args)
                        .skip(1)
                        .map(s -> Path.of(s).toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        SixMaxRootSequenceBatchFeedback.Result owned;
        if (args[0].equals("solve")) {
            try (var stream = Files.newOutputStream(paths.get(5), StandardOpenOption.CREATE_NEW)) {
                owned =
                        SixMaxRootSequenceBatchFeedback.solve(
                                SixMaxRootSequenceBatchFeedback.readRequest(paths.get(0)),
                                paths.get(1),
                                paths.get(2),
                                paths.get(3),
                                paths.get(4));
                SixMaxRootSequenceBatchFeedback.write(stream, owned);
            }
        } else
            owned =
                    SixMaxRootSequenceBatchFeedback.replay(
                            paths.get(0),
                            paths.get(1),
                            paths.get(2),
                            paths.get(3),
                            paths.get(4),
                            paths.get(5));
        System.out.println(
                SixMaxRootSequenceBatchFeedback.STATUS
                        + " qualifiedOffline="
                        + owned.report().qualifiedForOfflineDrill()
                        + " publicAdmission="
                        + owned.report().trainerAdmission());
    }
}
