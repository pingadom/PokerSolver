package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Offline projection/replay; never overwrites input or existing output artifacts. */
public final class SixMaxHistoryPhysicalStorageMain {
    private SixMaxHistoryPhysicalStorageMain() {}

    public static void main(String[] args) throws Exception {
        boolean project = args.length == 8 && "project".equals(args[0]);
        boolean replay = args.length == 8 && "replay".equals(args[0]);
        if (!project && !replay)
            throw new IllegalArgumentException(
                    "Usage: SixMaxHistoryPhysicalStorageMain <project|replay> <source> <rank-table> <original-table> <checkpoint> <study> <compact-table> <storage-audit>");
        var paths =
                Arrays.stream(args)
                        .skip(1)
                        .map(Path::of)
                        .map(p -> p.toAbsolutePath().normalize())
                        .toList();
        SixMaxTexturePayoffTableMain.distinct(paths);
        if (project && (Files.exists(paths.get(5)) || Files.exists(paths.get(6))))
            throw new IllegalArgumentException("Projection requires two new output paths");
        var source = SixMaxTexturePayoffTableMain.source(paths.get(0));
        var parent = SixMaxRankTexturePayoffTable.read(paths.get(1), source);
        var original = SixMaxHistoryPhysicalPayoffTable.replay(paths.get(2), source, parent);
        var cp = SixMaxHistoryPhysicalStudy.read(paths.get(3), source, parent, original);
        var predecessor =
                SixMaxHistoryPhysicalStudy.replayValidated(
                        paths.get(4), source, parent, original, cp);
        var compact =
                project
                        ? SixMaxHistoryPhysicalCompactStorage.project(original)
                        : SixMaxHistoryPhysicalCompactStorage.replay(paths.get(5), original);
        var audit =
                project
                        ? SixMaxHistoryPhysicalStorageAudit.assess(
                                source, parent, original, predecessor, compact)
                        : SixMaxHistoryPhysicalStorageAudit.replay(
                                paths.get(6), source, parent, original, predecessor, compact);
        if (project) {
            SixMaxHistoryPhysicalCompactStorage.write(paths.get(5), compact);
            SixMaxHistoryPhysicalStorageAudit.write(paths.get(6), audit);
        }
        System.out.println(
                "VALIDATION_ONLY identical states "
                        + audit.report().binding().completeTreeStates()
                        + " counts "
                        + audit.report().comparedCounts()
                        + " active shares "
                        + audit.report().comparedActiveShares()
                        + " local controls "
                        + audit.report().localControls().size()
                        + " layout "
                        + compact.layout());
    }
}
