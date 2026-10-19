package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RefinedConditionalStudyTest {
    static final Path INPUT =
            Path.of("../docs/data/supplied-paired-btn-facing-bet-conditional-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-paired-btn-facing-bet-conditional-report.json.gz");
    static SixMaxRefinedConditionalStudy.Result owned;

    @BeforeAll
    static void solve() throws Exception {
        owned =
                SixMaxRefinedConditionalStudy.solve(
                        SixMaxRefinedConditionalStudy.readRequest(INPUT));
    }

    @Test
    void completePairedFoldCallIntervalsKeepOriginalParentAndEndpointProvenance() throws Exception {
        var r = owned.report();
        assertEquals(r, SixMaxRefinedConditionalStudy.readReport(REPORT));
        assertFalse(r.trainerAdmission());
        assertEquals("BTN", r.context().actingSeat().name());
        assertEquals(List.of("8d", "Kc", "Kd"), r.context().board());
        assertEquals(9.75, r.context().potBb());
        assertEquals(3.25, r.context().toCallBb());
        assertEquals(List.of("fold", "call"), r.actions().stream().map(a -> a.action()).toList());
        var fold = r.actions().getFirst();
        var call = r.actions().getLast();
        assertEquals(-3, fold.lowerUtilityBb(), 2e-8);
        assertEquals(-3, fold.upperUtilityBb(), 2e-8);
        assertTrue(call.lowerUtilityBb() > .16 && call.upperUtilityBb() < .17);
        assertTrue(fold.lowerLossBb() > 3.16);
        assertTrue(call.upperLossBb() < .001);
        for (var action : r.actions()) {
            var a = action.diagnostic();
            assertEquals(r.parent().solve(), a.baseline());
            assertEquals(
                    FiniteTwoPlayerConditionalActionIntervals
                            .PHYSICAL_FLOP_ASCENDING_REFINED_ALGORITHM,
                    a.algorithm());
            assertTrue(a.certifiedReachLowerBound() > .2499);
            var witnesses = new ArrayList<FiniteTwoPlayerConditionalActionIntervals.Witness>();
            witnesses.add(a.minimumReachWitness());
            witnesses.add(a.maximumReachWitness());
            witnesses.add(a.lowerWitness());
            witnesses.addAll(a.upperWitnesses());
            assertEquals(4, witnesses.size());
            assertEquals(
                    witnesses.stream()
                            .mapToLong(w -> w.certificate().work().arithmeticWork())
                            .sum(),
                    a.work().intervalLpArithmeticWork());
            for (var witness : witnesses) {
                assertEquals(
                        BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM,
                        witness.certificate().algorithm());
                assertTrue(witness.certificate().absoluteDualityGap() < 1e-8);
                assertEquals(
                        1,
                        witness.posterior().stream().mapToDouble(p -> p.probability()).sum(),
                        1e-8);
            }
        }
        assertTrue(
                Arrays.stream(SixMaxRefinedConditionalStudy.Result.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }

    @Test
    void physicalReplayAndExclusivePlainGzipPersistencePreserveEveryField(@TempDir Path temp)
            throws Exception {
        assertEquals(owned.report(), SixMaxRefinedConditionalStudy.replay(INPUT, REPORT).report());
        for (String name : List.of("report.json", "report.json.gz")) {
            var path = temp.resolve(name);
            SixMaxRefinedConditionalStudy.write(path, owned);
            assertEquals(owned.report(), SixMaxRefinedConditionalStudy.readReport(path));
            assertThrows(
                    FileAlreadyExistsException.class,
                    () -> SixMaxRefinedConditionalStudy.write(path, owned));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRefinedConditionalStudy.replay(INPUT, INPUT));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRefinedConditionalStudyMain.main(
                                new String[] {"solve", INPUT.toString(), INPUT.toString()}));
    }

    @Test
    void completeReplayRejectsCallerEditedUtilityBounds(@TempDir Path temp) throws Exception {
        var tree = (ObjectNode) SixMaxTexturePayoffTable.mapper().valueToTree(owned.report());
        ((ObjectNode) tree.path("actions").get(0)).put("lowerUtilityBb", -2.9);
        var changed = temp.resolve("changed.json");
        Files.writeString(changed, tree.toString());
        assertNotEquals(owned.report(), SixMaxRefinedConditionalStudy.readReport(changed));
        assertEquals(
                "Complete refined conditional replay differs",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SixMaxRefinedConditionalStudy.replay(INPUT, changed))
                        .getMessage());
    }

    @Test
    void oldModeIdentityUnknownFieldsDuplicatesAndInputCapsReject(@TempDir Path temp)
            throws Exception {
        String original = Files.readString(INPUT);
        for (String changed :
                List.of(
                        original.replace(
                                SixMaxRefinedConditionalStudy.REQUEST_SCHEMA, "old-schema"),
                        original.replace(
                                FiniteTwoPlayerConditionalActionIntervals
                                        .PHYSICAL_FLOP_ASCENDING_REFINED_ALGORITHM,
                                FiniteTwoPlayerConditionalActionIntervals
                                        .PHYSICAL_FLOP_REFINED_DIRECTION_ALGORITHM),
                        original.replace("\"heroCards\"", "\"unexpected\""),
                        original + "{}",
                        original.replace("\"hero\":", "\"hero\":3,\"hero\":"),
                        original.replace("\"As Qs\"", "\"2c 2d\""))) {
            var path = temp.resolve("bad.json");
            Files.writeString(path, changed);
            assertThrows(Exception.class, () -> SixMaxRefinedConditionalStudy.readRequest(path));
        }
        var large = temp.resolve("large.json");
        Files.write(large, new byte[SixMaxRefinedConditionalStudy.MAX_BYTES + 1]);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRefinedConditionalStudy.readRequest(large));
        var zip = temp.resolve("large.gz");
        try (var stream = new java.util.zip.GZIPOutputStream(Files.newOutputStream(zip))) {
            stream.write(new byte[SixMaxRefinedConditionalStudy.MAX_BYTES + 1]);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRefinedConditionalStudy.readReport(zip));
    }

    @Test
    void afterCheckStillRejectsZeroReachUnderTheCompleteSecurityFace() throws Exception {
        var q = owned.report().request();
        var request =
                new SixMaxRefinedConditionalStudy.Request(
                        q.schemaVersion(),
                        q.parent(),
                        q.intervalAlgorithm(),
                        q.hero(),
                        q.heroCards(),
                        q.publicPreflopHistory(),
                        List.of("check"));
        var rejection =
                assertThrows(
                        FiniteTwoPlayerConditionalActionIntervals.Rejected.class,
                        () -> SixMaxRefinedConditionalStudy.solve(request));
        assertEquals(
                FiniteTwoPlayerConditionalActionIntervals.Failure.INSUFFICIENT_REACH,
                rejection.reason());
        var audit = rejection.reachFailure().orElseThrow();
        assertEquals(0, audit.minimumReachWitness().questionReach(), 1e-8);
        assertEquals(
                FiniteTwoPlayerAffineSequenceForm.ASCENDING_REFINED_DIRECTIONS_ALGORITHM,
                audit.baseline().algorithm());
        assertFalse(audit.trainerAdmission());
    }

    @Test
    void ascendingEndpointSolvesShareOneOriginalBudgetRatherThanResettingPerSolve()
            throws Exception {
        double[][] a = {{1}};
        double[] b = {1}, c = {1};
        var one = BoundedLinearProgram.solveAscendingRefinedDirections(a, b, c);
        long spent = one.certificate().work().arithmeticWork();
        int pivots = one.certificate().work().pivots();
        var budget =
                FiniteTwoPlayerRootActionIntervals.LpBudget.ascendingRefinedDirections(
                        2 * pivots, 2 * spent);
        budget.solve(a, b, c);
        budget.solve(a, b, c);
        assertEquals(2, budget.solves);
        assertEquals(2 * spent, budget.work);
        assertEquals(2 * pivots, budget.pivots);
        assertEquals(
                BoundedLinearProgram.Failure.WORK_LIMIT,
                assertThrows(BoundedLinearProgram.Rejected.class, () -> budget.solve(a, b, c))
                        .reason());
    }
}
