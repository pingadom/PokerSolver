package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.List;

/** Trusted operator-configured startup boundary. A pin alone never replaces physical replay. */
public final class SixMaxSuppliedIntervalTrainerLoader {
    private SixMaxSuppliedIntervalTrainerLoader() {}

    public static SixMaxSuppliedIntervalTrainer load(
            Path requestPath, Path reportPath, String expectedReportHash) throws Exception {
        if (requestPath == null
                || reportPath == null
                || expectedReportHash == null
                || !expectedReportHash.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException(
                    "Request, report and exact report hash pin required");
        requestPath = requestPath.toAbsolutePath().normalize();
        reportPath = reportPath.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath));
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        reportPath, SixMaxSuppliedIntervalFeedback.MAX_BYTES),
                                SixMaxSuppliedIntervalFeedback.Report.class);
        if (!expectedReportHash.equals(SixMaxHeadsUpPreflopGame.hash(saved)))
            throw new IllegalArgumentException("Interval qualification report pin differs");
        var owned = SixMaxSuppliedIntervalFeedback.replay(requestPath, reportPath);
        var trainer = new SixMaxSuppliedIntervalTrainer(owned);
        // Recheck the owned result as well: files could change during startup replay.
        if (!expectedReportHash.equals(trainer.question().reportHash()))
            throw new IllegalArgumentException("Owned interval qualification report pin differs");
        return trainer;
    }
}
