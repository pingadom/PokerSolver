package com.pokerlab.solver;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Offline exact generation or independent replay of a fixed public observation menu. */
public final class SixMaxHistoryPhysicalPayoffTableMain {
    private SixMaxHistoryPhysicalPayoffTableMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !(args[0].equals("generate") || args[0].equals("replay")))
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalPayoffTableMain <generate|replay> <source> <rank-table> <menu> <table>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        boolean generate = args[0].equals("generate");
        if (generate && Files.exists(paths.get(3)))
            throw new IllegalArgumentException("Generation requires a new output path");
        var menu = SixMaxHistoryPhysicalPayoffTable.readMenu(paths.get(2));
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var verified =
                generate
                        ? SixMaxHistoryPhysicalPayoffTable.generate(
                                source,
                                parent,
                                menu,
                                n -> System.out.println("Exact history/world rows " + n))
                        : SixMaxHistoryPhysicalPayoffTable.replay(paths.get(3), source, parent);
        if (!verified.artifact().menu().equals(menu))
            throw new IllegalArgumentException("Saved table uses a different public menu");
        if (generate) SixMaxHistoryPhysicalPayoffTable.write(paths.get(3), verified);
        System.out.println(
                "VALIDATION_ONLY history physical hash "
                        + SixMaxHistoryPhysicalPayoffTable.hash(verified.artifact())
                        + " states "
                        + verified.artifact().completeTreeStates()
                        + " observations "
                        + verified.artifact().observations().size());
    }
}
