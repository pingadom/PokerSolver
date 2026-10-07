package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

/** Full-deck generation offline, with guarded atomic optional-gzip export. */
public final class SixMaxRankTexturePayoffTableMain {
    private SixMaxRankTexturePayoffTableMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2)
            throw new IllegalArgumentException(
                    "Usage: SixMaxRankTexturePayoffTableMain <source> <table[.gz]>");
        var input = Path.of(args[0]).toAbsolutePath().normalize();
        var output = Path.of(args[1]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        var source = SixMaxTexturePayoffTableMain.source(input);
        var table =
                SixMaxRankTexturePayoffTable.generate(
                        source,
                        count ->
                                System.out.println(
                                        "VALIDATION_ONLY completed private world " + count));
        SixMaxRankTexturePayoffTable.writeBytes(
                output,
                SixMaxRankTexturePayoffTable.json(table).getBytes(StandardCharsets.UTF_8),
                4 * 1024 * 1024);
        System.out.println(
                "VALIDATION_ONLY rank/texture payoff table "
                        + SixMaxRankTexturePayoffTable.hash(table));
    }
}
