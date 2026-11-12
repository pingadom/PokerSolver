package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxRootHandBankRaiseFeedbackMain {
    private SixMaxRootHandBankRaiseFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException("solve|replay request bank minimum output.gz");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(a -> Path.of(a).toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        SixMaxRootHandBankRaiseFeedback.Result result;
        if (args[0].equals("solve"))
            try (var output = Files.newOutputStream(paths.get(3), StandardOpenOption.CREATE_NEW)) {
                result =
                        SixMaxRootHandBankRaiseFeedback.solve(
                                SixMaxRootHandBankRaiseFeedback.readRequest(paths.get(0)),
                                paths.get(1),
                                paths.get(2));
                SixMaxRootHandBankRaiseFeedback.write(output, result);
            }
        else
            result =
                    SixMaxRootHandBankRaiseFeedback.replay(
                            paths.get(0), paths.get(1), paths.get(2), paths.get(3));
        System.out.println(
                SixMaxRootHandBankRaiseFeedback.STATUS
                        + " qualifiedOffline="
                        + result.report().qualifiedForOfflineDrill()
                        + " publicAdmission="
                        + result.report().trainerAdmission());
    }
}
