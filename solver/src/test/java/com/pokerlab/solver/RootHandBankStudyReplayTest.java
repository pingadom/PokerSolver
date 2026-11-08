package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RootHandBankStudyReplayTest {
    @Test
    void oneOwnedPhysicalBankReproducesAllThreeOriginalHandReports() throws Exception {
        Path data = Path.of("../docs/data");
        var saved =
                SixMaxRootHandBankStudy.readReport(
                        data.resolve("supplied-root-hand-bank-report.json.gz"));
        var owned =
                SixMaxRootHandBankStudy.replay(
                        data.resolve("supplied-root-hand-bank-request.json"),
                        data.resolve("supplied-flop-size-menu-report.json.gz"),
                        data.resolve("supplied-root-hand-bank-report.json.gz"));
        assertEquals(saved, owned.report());
        assertEquals(3, owned.report().assessments().size());
        assertEquals(15, owned.report().completedWork().envelopeLps());
        assertTrue(owned.report().completeOuterBounds());
        assertEquals(java.util.List.of(0, 1, 2), owned.report().supportedQuestionIndices());
        assertFalse(owned.report().trainerAdmission());
    }
}
