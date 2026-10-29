package com.pokerlab.solver;

import java.nio.file.*;
import java.util.List;

public final class SixMaxSizeMenuHalfBtnMain {
    private SixMaxSizeMenuHalfBtnMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 3 || !List.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException("solve|replay request report");
        Path input = Path.of(args[1]).toAbsolutePath().normalize(),
                output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        if (args[0].equals("solve") && Files.exists(output))
            throw new IllegalArgumentException("Output already exists");
        var result =
                args[0].equals("solve")
                        ? SixMaxSizeMenuHalfBtnStudy.solve(
                                SixMaxSizeMenuHalfBtnStudy.readRequest(input))
                        : SixMaxSizeMenuHalfBtnStudy.replay(input, output);
        if (args[0].equals("solve")) SixMaxSizeMenuHalfBtnStudy.write(output, result);
        System.out.println(
                SixMaxSizeMenuHalfBtnStudy.STATUS
                        + " cases="
                        + result.report().cases().size()
                        + " qualified="
                        + result.report().qualifiedIndices());
    }
}
