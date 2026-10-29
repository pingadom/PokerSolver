package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxSizeMenuPreflopPlanMain {
    private SixMaxSizeMenuPreflopPlanMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !List.of("plan", "replay").contains(args[0]))
            throw new IllegalArgumentException("plan|replay request report");
        Path input = Path.of(args[1]).toAbsolutePath().normalize(),
                output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        if (args[0].equals("plan") && Files.exists(output))
            throw new IllegalArgumentException("Output already exists");
        var result =
                args[0].equals("plan")
                        ? SixMaxSizeMenuPreflopPlan.plan(
                                SixMaxSuppliedFlopSizeMenuStudy.readRequest(input))
                        : SixMaxSizeMenuPreflopPlan.replay(input, output);
        if (args[0].equals("plan")) SixMaxSizeMenuPreflopPlan.write(output, result);
        System.out.println(
                SixMaxSizeMenuPreflopPlan.STATUS
                        + " questions="
                        + result.report().questions().size()
                        + " overLimitActions="
                        + result.report().work().overPlanLimitActions());
    }
}
