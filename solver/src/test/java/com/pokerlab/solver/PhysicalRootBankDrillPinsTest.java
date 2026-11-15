package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class PhysicalRootBankDrillPinsTest {
    @Test
    void typedPhysicalAndPublicDigestsMatchCapturedBytesWithoutSolving() throws Exception {
        var report =
                SixMaxPhysicalRootBankStudy.readReport(
                        Path.of("../docs/data/supplied-physical-root-bank-reuse-report.json.gz"));
        assertEquals(
                SixMaxPhysicalRootBankDrillLoader.PHYSICAL_STUDY_HASH,
                SixMaxHeadsUpPreflopGame.hash(report));
        assertEquals(
                SixMaxPhysicalRootBankDrillLoader.STUDY_HASH,
                SixMaxHeadsUpPreflopGame.hash(report.historicalSource()));
        assertEquals(
                SixMaxPhysicalRootBankDrillLoader.StartupBudget.bounded(),
                SixMaxPhysicalRootBankDrillLoader.readManifest(
                                Path.of(
                                        "../docs/data/supplied-physical-root-bank-drill-manifest.json"))
                        .startupBudget());
    }
}
