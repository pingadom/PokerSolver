package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SixSeatTrainerCoverageAuditTest {
    @Test
    void treeWalkReconcilesWithSeededConditionalGradesAndAppliesReachFloor() {
        var ranges = List.of("AcKh,AdKh@0.3", "KcKd", "QcQd", "JcJd", "TcTd", "9c9d");
        var plan =
                SixSeatRangeSource.plan(
                        SixSeatRangeSourceTest.request(
                                ranges, SixSeatPayoffWork.Configuration.shared(4, 711)));
        var reference =
                SixSeatPayoffComparison.planExactReference(
                        plan,
                        SixSeatPayoffWork.Configuration.exactSuitReuse(),
                        new SixSeatPayoffWork.Budget(658008, 3948048));
        var source = plan.execute();
        var exact = reference.execute();
        var seedReplay =
                SixSeatTrainerActionAudit.assess(
                        source, exact, new SixSeatTrainerActionAudit.Options(512, 0, 0.01, 0.02));
        var coverage =
                SixSeatTrainerCoverageAudit.assess(
                        source, exact, new SixSeatTrainerCoverageAudit.Options(0, 0.01, 0.02));

        var totals = coverage.totals();
        assertEquals(totals.reachedPrompts(), totals.auditedPrompts());
        assertTrue(totals.auditedPrompts() >= seedReplay.distinctPrompts());
        assertTrue(totals.auditedPrompts() > totals.stablePrompts());
        assertEquals(
                totals.auditedPrompts(),
                coverage.seats().stream().mapToInt(s -> s.summary().auditedPrompts()).sum());
        assertEquals(
                totals.changedAcceptableSets(),
                coverage.seats().stream().mapToInt(s -> s.summary().changedAcceptableSets()).sum());

        var replay =
                seedReplay.prompts().stream()
                        .collect(
                                Collectors.toMap(
                                        SixSeatTrainerActionAudit.Row::prompt, row -> row));
        int overlap = 0;
        double previousReach = Double.POSITIVE_INFINITY;
        for (var failure : coverage.highestReachFailures()) {
            assertTrue(failure.decisionReach() > 0 && failure.decisionReach() <= previousReach);
            previousReach = failure.decisionReach();
            var row = replay.get(failure.prompt());
            if (row == null) continue;
            overlap++;
            assertFalse(row.stable());
            assertEquals(row.sampledAcceptableActions(), failure.sampledAcceptableActions());
            assertEquals(row.exactAcceptableActions(), failure.exactAcceptableActions());
            for (int i = 0; i < row.actions().size(); i++) {
                assertEquals(
                        row.actions().get(i).sampledLossBb(),
                        failure.actions().get(i).sampledLossBb(),
                        1e-13);
                assertEquals(
                        row.actions().get(i).exactLossBb(),
                        failure.actions().get(i).exactLossBb(),
                        1e-13);
            }
        }
        assertTrue(overlap > 0, "Bounded detail includes failures seen in replay");
        assertTrue(coverage.highestReachFailures().size() <= 32);

        var filtered =
                SixSeatTrainerCoverageAudit.assess(
                        source, exact, new SixSeatTrainerCoverageAudit.Options(.5, 0.01, 0.02));
        assertEquals(totals.reachedPrompts(), filtered.totals().reachedPrompts());
        assertTrue(filtered.totals().auditedPrompts() > 0);
        assertTrue(filtered.totals().auditedPrompts() < totals.auditedPrompts());
        assertTrue(filtered.highestReachFailures().stream().allMatch(f -> f.decisionReach() >= .5));
    }

    @Test
    void reachAndLossLimitsRequireFiniteValuesInRange() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerCoverageAudit.Options(1.01, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerCoverageAudit.Options(Double.NaN, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerCoverageAudit.Options(.1, Double.NaN, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixSeatTrainerCoverageAudit.Options(.1, 0, -.01));
    }
}
