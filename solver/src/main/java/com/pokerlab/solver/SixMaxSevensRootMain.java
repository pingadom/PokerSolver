package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxSevensRootMain {
    private SixMaxSevensRootMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException("solve|replay request pinned-parent report.gz");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                source = Path.of(args[2]).toAbsolutePath().normalize(),
                report = Path.of(args[3]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, source, report));
        SixMaxSevensRootStudy.Result result;
        if (args[0].equals("solve")) {
            try (var output = Files.newOutputStream(report, StandardOpenOption.CREATE_NEW)) {
                result =
                        SixMaxSevensRootStudy.solve(
                                SixMaxSevensRootStudy.readRequest(request), source);
                SixMaxSevensRootStudy.write(output, result);
            }
        } else result = SixMaxSevensRootStudy.replay(request, source, report);
        System.out.println(
                SixMaxSevensRootStudy.STATUS
                        + " complete="
                        + result.report().completeOuterBounds()
                        + " feedbackSupported="
                        + result.report().feedbackSupported()
                        + " reasons="
                        + result.report().rejectionReasons());
    }
}
