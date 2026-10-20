package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ResolvableRefinedConditionalStudyTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-weak-hand-resolvable-conditional-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-weak-hand-resolvable-conditional-report.json.gz");
    static SixMaxRefinedConditionalStudy.Result owned;
    static SixMaxSuppliedFlopBettingGame game;

    @BeforeAll
    static void fullPhysicalReplay() throws Exception {
        owned = SixMaxRefinedConditionalStudy.replay(REQUEST, REPORT);
        var c = owned.report().request().parent().connected();
        game = new SixMaxSuppliedFlopBettingGame(c.input(), c.flop(), c.betPotFraction());
    }

    @Test
    void oneWeakHandProducesAFullResearchReportWithAnUnchangedParentAlgorithm() {
        var r = owned.report();
        assertEquals(
                SixMaxRefinedConditionalStudy.RESOLVABLE_BASIS_REPORT_SCHEMA, r.schemaVersion());
        assertEquals("2c 3c", r.request().heroCards());
        assertFalse(r.trainerAdmission());
        assertEquals(List.of("fold", "call"), r.context().legalActions());
        assertEquals(6, r.context().players().size());
        assertEquals(3.25, r.context().toCallBb());
        assertEquals(24, r.parent().originalJointPrior().size());
        assertEquals(15_792_192, r.parent().binding().budget().sourceBudget().enumeratedBoards());
        assertEquals(
                FiniteTwoPlayerAffineSequenceForm.ASCENDING_REFINED_DIRECTIONS_ALGORITHM,
                r.parent().solve().algorithm());
        assertEquals(
                BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM,
                r.parent().firstLpWitness().certificate().algorithm());
        assertEquals(
                BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM,
                r.parent().secondLpWitness().certificate().algorithm());
        assertTrue(r.parent().solve().behavioralQuality().nashConvBb() <= 1e-8);
        assertEquals(-3, r.actions().getFirst().lowerUtilityBb(), 2e-8);
        assertTrue(r.actions().getFirst().upperLossBb() < .01);
        assertTrue(r.actions().getLast().lowerLossBb() > 2.24);
        for (var a : r.actions()) {
            var d = a.diagnostic();
            assertFalse(d.trainerAdmission());
            assertEquals(r.parent().solve(), d.baseline());
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals
                            .PHYSICAL_FLOP_RESOLVABLE_REFINED_BASIS_ALGORITHM,
                    d.algorithm());
            assertTrue(d.certifiedReachLowerBound() >= .0001);
            var witnesses = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Witness>();
            witnesses.add(d.minimumReachWitness());
            witnesses.add(d.maximumReachWitness());
            witnesses.add(d.lowerWitness());
            witnesses.addAll(d.upperWitnesses());
            assertEquals(4, witnesses.size());
            assertEquals(
                    witnesses.stream()
                            .mapToLong(w -> w.certificate().work().arithmeticWork())
                            .sum(),
                    d.work().intervalLpArithmeticWork());
            assertTrue(d.work().intervalLpArithmeticWork() <= 4_000_000_000L);
            for (var w : witnesses)
                assertEquals(
                        BoundedLinearProgram.RESOLVABLE_REFINED_BASIS_ALGORITHM,
                        w.certificate().algorithm());
        }
    }

    @Test
    void theOldExplicitV7ModeStillRejectsThisInputWithoutARetry() {
        var r = owned.report().request();
        var old =
                new SixMaxRefinedConditionalStudy.Request(
                        r.schemaVersion(),
                        r.parent(),
                        FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM,
                        r.hero(),
                        r.heroCards(),
                        r.publicPreflopHistory(),
                        r.flopActions());
        var error =
                assertThrows(
                        BoundedLinearProgram.Rejected.class,
                        () -> SixMaxRefinedConditionalStudy.solve(old, game));
        assertEquals(BoundedLinearProgram.Failure.NUMERICAL_FAILURE, error.reason());
        assertEquals("Unresolved positive LP reduced cost", error.getMessage());
    }

    @Test
    void schemaAndModeAreBoundAndSerializedReportsDoNotManufactureOwnedResults(@TempDir Path dir)
            throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        for (String schema :
                List.of(
                        SixMaxRefinedConditionalStudy.REPORT_SCHEMA,
                        SixMaxRefinedConditionalStudy.BASIS_REPORT_SCHEMA)) {
            var value = (ObjectNode) m.valueToTree(owned.report());
            value.put("schemaVersion", schema);
            assertThrows(
                    Exception.class,
                    () -> m.treeToValue(value, SixMaxRefinedConditionalStudy.Report.class));
        }
        var value = (ObjectNode) m.valueToTree(owned.report());
        ((ObjectNode) value.path("request"))
                .put(
                        "intervalAlgorithm",
                        FiniteTwoPlayerConditionalActionIntervals
                                .PHYSICAL_FLOP_REFINED_BASIS_ALGORITHM);
        assertThrows(
                Exception.class,
                () -> m.treeToValue(value, SixMaxRefinedConditionalStudy.Report.class));
        assertTrue(
                Arrays.stream(SixMaxRefinedConditionalStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
        var output = dir.resolve("research.json.gz");
        SixMaxRefinedConditionalStudy.write(output, owned);
        assertEquals(owned.report(), SixMaxRefinedConditionalStudy.readReport(output));
        assertThrows(
                FileAlreadyExistsException.class,
                () -> SixMaxRefinedConditionalStudy.write(output, owned));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRefinedConditionalStudy.replay(REQUEST, REQUEST));
    }

    @Test
    void anUnstableNearZeroReachWitnessRejectsWithoutAPosteriorOrRetry() {
        var r = owned.report().request();
        var zero =
                new SixMaxRefinedConditionalStudy.Request(
                        r.schemaVersion(),
                        r.parent(),
                        r.intervalAlgorithm(),
                        3,
                        "As Qs",
                        r.publicPreflopHistory(),
                        List.of("check"));
        var e =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () -> SixMaxRefinedConditionalStudy.solve(zero, game));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.NUMERICAL_FAILURE, e.reason());
        assertEquals(
                "Resolvable refined endpoint witness failed: Invalid behavioral ratio",
                e.getMessage());
        assertTrue(e.reachFailure().isEmpty());
        assertInstanceOf(IllegalStateException.class, e.getCause());
    }
}
