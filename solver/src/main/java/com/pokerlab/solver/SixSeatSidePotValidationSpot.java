package com.pokerlab.solver;

import java.util.List;

/** Two synthetic combos per seat, with unequal stacks for exact side-pot research. */
public final class SixSeatSidePotValidationSpot {
    private SixSeatSidePotValidationSpot() {}

    public static MultiwaySidePotSpot create() {
        return new MultiwaySidePotSpot(
                "six-seat-side-pot-diverse-validation",
                List.of(PreflopAllInSpot.Seat.values()),
                SixSeatValidationSpot.create().ranges(),
                List.of(30.0, 1.0, 2.0, 0.0, 0.5, 1.0),
                List.of(30.0, 10.0, 20.0, 15.0, 25.0, 5.0),
                0);
    }
}
