package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.List;

/** Trusted operator-configured startup boundary. A pin alone never replaces physical replay. */
public final class SixMaxSuppliedFlopTrainerLoader {
    private SixMaxSuppliedFlopTrainerLoader() {}

    public static SixMaxSuppliedFlopTrainer load(
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
                                        reportPath, SixMaxSuppliedFlopFeedback.MAX_BYTES),
                                SixMaxSuppliedFlopFeedback.Report.class);
        if (!expectedReportHash.equals(SixMaxHeadsUpPreflopGame.hash(saved)))
            throw new IllegalArgumentException("Flop qualification report pin differs");
        var owned = SixMaxSuppliedFlopFeedback.replay(requestPath, reportPath);
        var trainer = new SixMaxSuppliedFlopTrainer(owned);
        // Recheck the owned result as well: files could change during startup replay.
        if (!expectedReportHash.equals(trainer.question().reportHash()))
            throw new IllegalArgumentException("Owned flop qualification report pin differs");
        return trainer;
    }
}
