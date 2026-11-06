package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxRootSequenceBatchMain {
    private SixMaxRootSequenceBatchMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "solve|replay request pinned-original-envelope pinned-sequence-minimum report.gz");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                source = Path.of(args[2]).toAbsolutePath().normalize(),
                previous = Path.of(args[3]).toAbsolutePath().normalize(),
                output = Path.of(args[4]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, source, previous, output));
        SixMaxRootSequenceBatchStudy.Result result;
        if (args[0].equals("solve")) {
            try (var stream = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                result =
                        SixMaxRootSequenceBatchStudy.solve(
                                SixMaxRootSequenceBatchStudy.readRequest(request),
                                source,
                                previous);
                SixMaxRootSequenceBatchStudy.write(stream, result);
            }
        } else result = SixMaxRootSequenceBatchStudy.replay(request, source, previous, output);
        System.out.println(
                SixMaxRootSequenceBatchStudy.STATUS
                        + " completeMinima="
                        + result.report().completeMinima()
                        + " referenceSupported="
                        + result.report().referenceSupportedMinima());
    }
}
