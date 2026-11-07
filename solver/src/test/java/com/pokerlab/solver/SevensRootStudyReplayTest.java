package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SevensRootStudyReplayTest {
    @Test
    void oneOwnedPhysicalSolveReproducesTheCompletePublishedSevensReport() throws Exception {
        Path data = Path.of("../docs/data");
        var saved =
                SixMaxSevensRootStudy.readReport(
                        data.resolve("supplied-sevens-root-study-report.json.gz"));
        var owned =
                SixMaxSevensRootStudy.replay(
                        data.resolve("supplied-sevens-root-study-request.json"),
                        data.resolve("supplied-flop-size-menu-report.json.gz"),
                        data.resolve("supplied-sevens-root-study-report.json.gz"));
        assertEquals(saved, owned.report());
        assertTrue(owned.report().completeOuterBounds());
        assertTrue(owned.report().feedbackSupported());
        assertFalse(owned.report().trainerAdmission());
    }
}
