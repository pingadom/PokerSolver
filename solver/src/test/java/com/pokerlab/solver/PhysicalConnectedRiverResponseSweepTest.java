package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class PhysicalConnectedRiverResponseSweepTest {
    @Test
    void preservesBudgetAndSeedOrderAndAccountsForAllAttemptedDeals() {
        var rows =
                PhysicalConnectedRiverResponseSweep.run(
                        ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3,
                        List.of(20, 40),
                        List.of(42L, 43L),
                        500,
                        1);
        assertEquals(4, rows.size());
        assertEquals(List.of(20, 20, 40, 40), rows.stream().map(r -> r.iterations()).toList());
        assertEquals(List.of(42L, 43L, 42L, 43L), rows.stream().map(r -> r.seed()).toList());
        assertEquals(
                rows,
                PhysicalConnectedRiverResponseSweep.run(
                        ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3,
                        List.of(20, 40),
                        List.of(42L, 43L),
                        500,
                        1));
        for (var row : rows) {
            assertEquals(
                    500,
                    row.audit().terminalBeforeRiver()
                            + row.audit().missingReachPolicy()
                            + row.audit().reachedRiver());
            assertTrue(row.primaryInformationSets() > 0);
            assertTrue(row.alternateInformationSets() > 0);
        }
    }

    @Test
    void rejectsAmbiguousOrImpossibleSweepsBeforeSolving() {
        var profile = ButtonBigBlindRangeValidationFixture.RangeProfile.VALIDATION_3X3;
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalConnectedRiverResponseSweep.run(
                                profile, List.of(), List.of(42L), 500, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalConnectedRiverResponseSweep.run(
                                profile, List.of(20, 20), List.of(42L), 500, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalConnectedRiverResponseSweep.run(
                                profile, List.of(20), List.of(42L, 42L), 500, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        PhysicalConnectedRiverResponseSweep.run(
                                profile, List.of(20), List.of(42L), 1, 1));
    }
}
