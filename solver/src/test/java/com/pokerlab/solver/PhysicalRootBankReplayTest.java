package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import org.junit.jupiter.api.Test;

class PhysicalRootBankReplayTest {
    @Test
    void completeOwnedPhysicalReplayReproducesOriginalMenusOnce() throws Exception {
        Path data = Path.of("../docs/data");
        var saved =
                SixMaxPhysicalRootBankStudy.readReport(
                        data.resolve("supplied-physical-root-bank-reuse-report.json.gz"));
        var owned =
                SixMaxPhysicalRootBankStudy.replay(
                        data.resolve("supplied-physical-root-bank-reuse-request.json"),
                        data.resolve("supplied-root-hand-bank-report.json.gz"),
                        data.resolve("supplied-physical-root-bank-reuse-report.json.gz"));
        assertEquals(saved, owned.report());
        assertTrue(owned.report().originalMenusReproduced());
        assertFalse(owned.report().trainerAdmission());
        assertEquals(2, owned.report().maximumWork().physicalParentLpSolves());
        assertEquals(0, owned.report().batch().work().parentLpSolves());
        assertEquals(15, owned.report().batch().work().endpointLpSolves());
        assertTrue(
                owned.report().comparisons().stream()
                        .allMatch(SixMaxPhysicalRootBankStudy.Comparison::identical));
    }
}
