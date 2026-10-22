package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WeakRangeFlopCoverageReplayTest {
    static SixMaxRefinedFlopCoverageStudy.Result owned;

    @BeforeAll
    static void fullOrderedReplayOnce() throws Exception {
        owned =
                SixMaxRefinedFlopCoverageStudy.replay(
                        RefinedFlopCoverageStudyTest.REQUEST, RefinedFlopCoverageStudyTest.REPORT);
    }

    @Test
    void completePhysicalReplayRetainsAllEightCasesAndEveryNumericalRejection() throws Exception {
        var r = owned.report();
        assertFalse(r.trainerAdmission());
        assertEquals(new SixMaxRefinedFlopCoverageStudy.Counts(8, 4, 4, 4, 0), r.counts());
        assertEquals(0, r.reservation().freshReferenceRuns());
        assertEquals(0, r.reservation().automaticRetries());
        assertEquals(15_792_192, r.binding().budget().sourceBudget().enumeratedBoards());
        for (int i = 0; i < 8; i++) {
            var item = r.cases().get(i);
            if (i % 2 == 0) {
                assertEquals(
                        SixMaxRefinedFlopCoverageStudy.Outcome.CONDITIONAL_REJECTED,
                        item.outcome());
                assertEquals("NUMERICAL_FAILURE", item.rejection().reason());
                assertTrue(item.rejection().detail().contains("Invalid behavioral ratio"));
                assertTrue(item.moves().isEmpty());
                assertNull(item.conditional());
            } else {
                assertEquals(
                        SixMaxRefinedFlopCoverageStudy.Outcome.ACTION_SEPARATION, item.outcome());
                assertEquals(List.of("fold", "call"), item.conditional().context().legalActions());
                assertNull(item.rejection());
            }
        }
        var old =
                SixMaxRefinedConditionalStudy.readReport(
                        Path.of(
                                "../docs/data/supplied-weak-hand-resolvable-conditional-report.json.gz"));
        assertEquals(old, r.cases().get(1).conditional());
        assertEquals(
                "8a4a1f7c7c038b6de8d7d7488031e8e6c5c5ec9c228bfff0afd8a446d6560028",
                SixMaxHeadsUpPreflopGame.hash(r));
    }

    @Test
    void ownedReportRoundTripsWithoutOverwriteOrManufacturingAGradingHandle(@TempDir Path folder)
            throws Exception {
        var output = folder.resolve("coverage.json.gz");
        SixMaxRefinedFlopCoverageStudy.write(output, owned);
        assertEquals(owned.report(), SixMaxRefinedFlopCoverageStudy.readReport(output));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRefinedFlopCoverageStudy.write(output, owned));
        assertTrue(
                Arrays.stream(SixMaxRefinedFlopCoverageStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }
}
