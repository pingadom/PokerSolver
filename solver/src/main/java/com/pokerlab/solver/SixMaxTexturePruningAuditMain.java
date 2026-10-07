package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.Arrays;

public final class SixMaxTexturePruningAuditMain {
    private SixMaxTexturePruningAuditMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5)
            throw new IllegalArgumentException(
                    "Usage: SixMaxTexturePruningAuditMain <source> <table> <original-checkpoint> <pruned-checkpoint> <report>");
        var paths =
                Arrays.stream(args).map(Path::of).map(p -> p.toAbsolutePath().normalize()).toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var table = SixMaxTexturePayoffTable.read(paths.get(1), source);
        var original = SixMaxTextureStudy.read(paths.get(2), source, table);
        var pruned = SixMaxTextureStudy.read(paths.get(3), source, table);
        var report = SixMaxTexturePruningAudit.assess(source, table, original, pruned);
        SixMaxTexturePayoffTableMain.atomicWrite(paths.get(4), SixMaxTextureStudy.json(report));
        System.out.println(
                "VALIDATION_ONLY maximum action-frequency difference "
                        + report.maximumActionFrequencyDifference());
    }
}
