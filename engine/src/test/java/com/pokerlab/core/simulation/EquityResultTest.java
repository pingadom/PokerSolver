package com.pokerlab.core.simulation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EquityResultTest {

    @Test
    void calculatesEquityUsingWinsAndHalfOfTies() {
        EquityResult result = new EquityResult(45, 50, 5, 100, 12);

        assertEquals(0.475, result.heroEquity(), 0.000001);
        assertEquals(0.525, result.villainEquity(), 0.000001);
        assertEquals(0.05, result.tieRate(), 0.000001);
    }

    @Test
    void rejectsCountsThatDoNotAddUpToSimulationTotal() {
        assertThrows(IllegalArgumentException.class, () -> new EquityResult(45, 50, 5, 101, 12));
    }
}
