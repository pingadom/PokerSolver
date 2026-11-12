package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxRootHandBankRaiseMain {
    private SixMaxRootHandBankRaiseMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException("solve|replay request pinned-bank report.gz");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                bank = Path.of(args[2]).toAbsolutePath().normalize(),
                report = Path.of(args[3]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, bank, report));
        SixMaxRootHandBankRaiseStudy.Result result;
        if (args[0].equals("solve")) {
            try (var output = Files.newOutputStream(report, StandardOpenOption.CREATE_NEW)) {
                result =
                        SixMaxRootHandBankRaiseStudy.solve(
                                SixMaxRootHandBankRaiseStudy.readRequest(request), bank);
                SixMaxRootHandBankRaiseStudy.write(output, result);
            }
        } else result = SixMaxRootHandBankRaiseStudy.replay(request, bank, report);
        System.out.println(
                SixMaxRootHandBankRaiseStudy.STATUS
                        + " complete="
                        + result.report().completeMinima()
                        + " referenceSupported="
                        + result.report().referenceSupportedMinima()
                        + " reasons="
                        + result.report().rejectionReasons());
    }
}
