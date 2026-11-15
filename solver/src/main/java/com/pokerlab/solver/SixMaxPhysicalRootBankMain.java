package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxPhysicalRootBankMain {
    private SixMaxPhysicalRootBankMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "solve|replay request pinned-original-bank report.gz");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                source = Path.of(args[2]).toAbsolutePath().normalize(),
                report = Path.of(args[3]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, source, report));
        SixMaxPhysicalRootBankStudy.Result result;
        if (args[0].equals("solve")) {
            try (var output = Files.newOutputStream(report, StandardOpenOption.CREATE_NEW)) {
                result =
                        SixMaxPhysicalRootBankStudy.solve(
                                SixMaxPhysicalRootBankStudy.readRequest(request), source);
                SixMaxPhysicalRootBankStudy.write(output, result);
            }
        } else result = SixMaxPhysicalRootBankStudy.replay(request, source, report);
        System.out.println(
                SixMaxPhysicalRootBankStudy.STATUS
                        + " complete="
                        + result.report().originalMenusReproduced()
                        + " parentLpSolves=2 endpointLpSolves="
                        + result.report().batch().work().endpointLpSolves());
    }
}
