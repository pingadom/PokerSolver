package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

/** Exclusive-output research CLI. Preflight performs no payoff enumeration or optimization. */
public final class SixMaxSuppliedFlopSizeMenuMain {
    private SixMaxSuppliedFlopSizeMenuMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !List.of("preflight", "solve", "replay").contains(args[0]))
            throw new IllegalArgumentException("preflight|solve|replay request report");
        Path request = Path.of(args[1]).toAbsolutePath().normalize();
        Path report = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report));
        if (!args[0].equals("replay") && Files.exists(report))
            throw new IllegalArgumentException("Output already exists");
        var input = SixMaxSuppliedFlopSizeMenuStudy.readRequest(request);
        if (args[0].equals("preflight")) {
            var budget =
                    SixMaxSuppliedFlopSizeMenuGame.preflight(
                            input.input(), input.flop(), input.menu());
            Files.createDirectories(report.getParent());
            Files.writeString(
                    report, SixMaxTextureStudy.json(budget), StandardOpenOption.CREATE_NEW);
        } else {
            var result =
                    args[0].equals("solve")
                            ? SixMaxSuppliedFlopSizeMenuStudy.solve(input)
                            : SixMaxSuppliedFlopSizeMenuStudy.replay(request, report);
            if (args[0].equals("solve")) SixMaxSuppliedFlopSizeMenuStudy.write(report, result);
            System.out.println(
                    SixMaxSuppliedFlopSizeMenuStudy.STATUS + " " + result.report().solutionHash());
        }
    }
}
