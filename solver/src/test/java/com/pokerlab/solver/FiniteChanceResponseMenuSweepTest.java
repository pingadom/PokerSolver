package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class FiniteChanceResponseMenuSweepTest {
    @Test
    void pairsChanceModesWithinEachFiniteGameAndAccountsForRootWork() {
        var physical = ButtonBigBlindRangeValidationFixture.createCoarseBucketed();
        var report =
                FiniteChanceResponseMenuSweep.assess(physical, 1, List.of(142L, 143L), 30, 90, 42);
        assertEquals(
                report,
                FiniteChanceResponseMenuSweep.assess(physical, 1, List.of(142L, 143L), 30, 90, 42));
        assertEquals(2, report.menus().size());
        assertEquals(9, report.menus().getFirst().rootDeals());
        assertNotEquals(
                report.menus().getFirst().flopQuantiles(),
                report.menus().getLast().flopQuantiles());
        for (var menu : report.menus()) {
            assertTrue(menu.rootDeals() * menu.exactRootIterations() <= report.sampledIterations());
            assertEquals(2, menu.players().size());
            for (int player = 0; player <= 1; player++) {
                var result = menu.player(player);
                assertEquals(player, result.player());
                assertTrue(result.exactResponseGainBb() >= -1e-8);
                assertTrue(result.sampledFinalShortfallBb() >= 0);
                assertTrue(result.exactRootFinalShortfallBb() >= 0);
            }
        }
        assertEquals(2, report.summary(0).menus());
        assertThrows(IllegalArgumentException.class, () -> report.summary(2));
    }

    @Test
    void rejectsRepeatedMenusAndInsufficientDealBudget() {
        var physical = ButtonBigBlindRangeValidationFixture.createCoarseBucketed();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FiniteChanceResponseMenuSweep.assess(
                                physical, 1, List.of(142L, 142L), 30, 90, 42));
        assertThrows(
                IllegalArgumentException.class,
                () -> FiniteChanceResponseMenuSweep.assess(physical, 1, List.of(142L), 30, 8, 42));
    }
}
