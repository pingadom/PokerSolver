package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;

class RefinedBasisConditionalStudyTest {
    static final List<String> LABELS = List.of("js9s", "ahkh", "qhth", "asqs");
    static final List<SixMaxRefinedConditionalStudy.Result> OWNED = new ArrayList<>();

    static Path path(String label, String suffix) {
        return Path.of(
                "../docs/data/supplied-paired-basis-btn-" + label + "-conditional-" + suffix);
    }

    @BeforeAll
    static void replayAll() throws Exception {
        for (String label : LABELS)
            OWNED.add(
                    SixMaxRefinedConditionalStudy.replay(
                            path(label, "request.json"), path(label, "report.json.gz")));
    }

    @Test
    void allFourPhysicalStudiesReplayWithTheUnchangedParentAndNewEndpointMode() throws Exception {
        var parent =
                SixMaxRefinedParentStudy.readReport(
                        Path.of("../docs/data/supplied-paired-flop-refined-parent-report.json.gz"));
        for (int i = 0; i < OWNED.size(); i++) {
            var r = OWNED.get(i).report();
            assertEquals(SixMaxRefinedConditionalStudy.BASIS_REPORT_SCHEMA, r.schemaVersion());
            assertFalse(r.trainerAdmission());
            assertEquals(parent, r.parent());
            assertEquals(
                    r,
                    SixMaxRefinedConditionalStudy.readReport(
                            path(LABELS.get(i), "report.json.gz")));
            assertEquals(List.of("fold", "call"), r.context().legalActions());
            assertEquals(6, r.context().players().size());
            assertEquals(3.25, r.context().toCallBb());
            assertTrue(r.actions().get(0).lowerLossBb() > .01);
            assertTrue(r.actions().get(1).upperLossBb() < .01);
            for (var action : r.actions()) {
                var a = action.diagnostic();
                assertEquals(
                        FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM,
                        a.algorithm());
                assertEquals(parent.solve(), a.baseline());
                // Reach witnesses share the same original LP certificate protocol.
                assertEquals(
                        BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                        a.minimumReachWitness().certificate().algorithm());
                assertEquals(
                        BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                        a.maximumReachWitness().certificate().algorithm());
                assertEquals(
                        BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                        a.lowerWitness().certificate().algorithm());
                for (var w : a.upperWitnesses())
                    assertEquals(
                            BoundedLinearProgram.ASCENDING_REFINED_BASIS_ALGORITHM,
                            w.certificate().algorithm());
                assertEquals(4, a.work().intervalLpSolves());
                assertTrue(
                        a.work().intervalLpArithmeticWork()
                                <= BoundedLinearProgram.MAX_ARITHMETIC_WORK);
                assertTrue(a.work().compilerUnits() <= a.work().compilerLimit());
            }
        }
    }

    @Test
    void reportVersionAndExplicitModeCannotBeSwappedByParsing() throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var tree = (ObjectNode) mapper.valueToTree(OWNED.getFirst().report());
        tree.put("schemaVersion", SixMaxRefinedConditionalStudy.REPORT_SCHEMA);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(tree, SixMaxRefinedConditionalStudy.Report.class));
        tree.put("schemaVersion", SixMaxRefinedConditionalStudy.BASIS_REPORT_SCHEMA);
        ((ObjectNode) tree.path("request"))
                .put(
                        "intervalAlgorithm",
                        FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_ASCENDING_REFINED_ALGORITHM);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(tree, SixMaxRefinedConditionalStudy.Report.class));
        assertTrue(
                Arrays.stream(SixMaxRefinedConditionalStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void zeroReachStillRejectsWithoutAnAutomaticRetryOrManufacturedPosterior() throws Exception {
        var r = OWNED.getLast().report().request();
        var zero =
                new SixMaxRefinedConditionalStudy.Request(
                        r.schemaVersion(),
                        r.parent(),
                        r.intervalAlgorithm(),
                        r.hero(),
                        r.heroCards(),
                        r.publicPreflopHistory(),
                        List.of("check"));
        var rejected =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () -> SixMaxRefinedConditionalStudy.solve(zero));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                rejected.reason());
        var witness = rejected.reachFailure().orElseThrow();
        assertEquals(0, witness.minimumReachWitness().questionReach(), 1e-8);
        assertFalse(witness.trainerAdmission());
    }
}
