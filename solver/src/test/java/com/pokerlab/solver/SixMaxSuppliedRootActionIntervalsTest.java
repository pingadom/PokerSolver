package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxSuppliedRootActionIntervalsTest {
    static SixMaxSuppliedRootActionIntervals.Result result;

    @BeforeAll
    static void replay() throws Exception {
        result =
                SixMaxSuppliedRootActionIntervals.replay(
                        SixMaxSuppliedRootDecisionIntervalsTest.data("raise-request"),
                        SixMaxSuppliedRootDecisionIntervalsTest.data("raise-report"));
    }

    @Test
    void singleActionReplaysEveryLpFlowAndInformationSetResponseCertificate() {
        var r = result.report();
        var d = r.diagnostic();
        assertFalse(r.trainerAdmission());
        assertEquals(1, r.heroCommittedBb());
        assertEquals(.1955340277314056, r.lowerEvBb(), 2e-8);
        assertEquals(.728954145812905, r.upperEvBb(), 2e-8);
        assertEquals(d.lowerUtility() + r.heroCommittedBb(), r.lowerEvBb());
        assertEquals(d.upperUtility() + r.heroCommittedBb(), r.upperEvBb());
        var witnesses = new ArrayList<>(d.upperWitnesses());
        witnesses.add(d.lowerWitness());
        for (var w : witnesses) {
            assertTrue(w.certificate().absoluteDualityGap() <= 1e-8);
            assertTrue(w.certificate().maximumPrimalViolation() <= 1e-8);
            assertTrue(w.certificate().maximumDualViolation() <= 1e-8);
            assertTrue(w.opponentFlow().maximumResidual() <= 1e-8);
            assertTrue(
                    w.globalHeroBestResponse()
                            <= d.globalHeroUpperValue() + d.securitySlack() + 1e-8);
        }
        assertEquals(d.lowerUtility(), d.lowerWitness().conditionalHeroBestResponse(), 1e-8);
        assertEquals(
                d.upperUtility(),
                d.upperWitnesses().get(d.upperPlan()).conditionalHeroBestResponse(),
                1e-8);
        assertEquals(160, d.work().intervalLpPivots());
        assertTrue(d.work().compilerUnits() < 23000);
    }

    @Test
    void savedLpNumbersCannotManufactureAnInterval(@TempDir Path dir) throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(result.report());
        ((ObjectNode) tree.path("diagnostic")).put("lowerUtility", -1.0);
        tree.put(
                "lowerEvBb",
                0.0); // Internally consistent shifted number, still not replay evidence.
        Path tampered = dir.resolve("fake.json");
        Files.writeString(tampered, mapper.writeValueAsString(tree));
        var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                SixMaxSuppliedRootActionIntervals.replay(
                                        SixMaxSuppliedRootDecisionIntervalsTest.data(
                                                "raise-request"),
                                        tampered));
        assertTrue(failure.getMessage().contains("Exact root interval replay differs"));
    }

    @Test
    void outputPathsAndGzipRemainStrict(@TempDir Path dir) throws Exception {
        Path output = dir.resolve("report.json.gz");
        SixMaxSuppliedRootActionIntervals.write(output, result);
        assertEquals(
                result.report(),
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        output, SixMaxSuppliedRootActionIntervals.MAX_BYTES),
                                SixMaxSuppliedRootActionIntervals.Report.class));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxSuppliedRootActionIntervals.write(output, result));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRootActionIntervals.replay(output, output));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxSuppliedRootActionIntervalsMain.main(
                                new String[] {"solve", "missing.json", output.toString()}));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRootActionIntervalsMain.main(new String[0]));
    }
}
