package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.Arrays;

/** Offline reached-signal diagnostics and fixed-preflop unilateral witnesses; no policy export. */
public final class SixMaxRankTextureConditionalAuditMain {
    private SixMaxRankTextureConditionalAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !(args[0].equals("audit") || args[0].equals("replay")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxRankTextureConditionalAuditMain <audit|replay> <source> <table> <checkpoint> <report[.gz]>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var table = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var cp = SixMaxRankTextureStudy.read(paths.get(2), source, table);
        var report =
                args[0].equals("replay")
                        ? SixMaxRankTextureConditionalAudit.replay(paths.get(3), source, table, cp)
                        : SixMaxRankTextureConditionalAudit.assess(source, table, cp);
        if (args[0].equals("audit")) SixMaxRankTextureConditionalAudit.write(paths.get(3), report);
        System.out.println(
                "VALIDATION_ONLY "
                        + args[0]
                        + " signals "
                        + report.summary().auditedSignals()
                        + " maximum conditional gap "
                        + report.summary().largestConditionalGapBb()
                        + " reach-weighted gap "
                        + report.parentWitness().reachWeightedLocalNashConvBb());
    }
}
