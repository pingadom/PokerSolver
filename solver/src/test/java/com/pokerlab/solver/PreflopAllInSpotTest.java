package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class PreflopAllInSpotTest {
    @Test
    void validationSpotReconstructsChipFlowAndStableHash() {
        PreflopAllInSpot spot = ValidationSpot.create();
        assertEquals(22, spot.firstCommittedBb());
        assertEquals(40, spot.secondCommittedBb());
        assertEquals(1.5, spot.deadMoneyBb());
        assertEquals(64, spot.contentHash().length());

        var reversedFirst = new ArrayList<>(spot.firstRange());
        var reversedSecond = new ArrayList<>(spot.secondRange());
        Collections.reverse(reversedFirst);
        Collections.reverse(reversedSecond);
        PreflopAllInSpot reordered =
                new PreflopAllInSpot(
                        spot.id(),
                        spot.effectiveStackBb(),
                        spot.smallBlindBb(),
                        spot.firstSeat(),
                        spot.secondSeat(),
                        spot.priorActions(),
                        reversedFirst,
                        reversedSecond);
        assertEquals(spot.contentHash(), reordered.contentHash());
        assertEquals(spot.contentHash(), ValidationSpot.create().contentHash());

        PreflopAllInSpot changed =
                new PreflopAllInSpot(
                        spot.id(),
                        spot.effectiveStackBb(),
                        spot.smallBlindBb(),
                        spot.firstSeat(),
                        spot.secondSeat(),
                        spot.priorActions(),
                        spot.firstRange(),
                        spot.secondRange().stream()
                                .map(
                                        combo ->
                                                new WeightedCombo(
                                                        combo.first(),
                                                        combo.second(),
                                                        combo.weight() * 2))
                                .toList());
        assertNotEquals(spot.contentHash(), changed.contentHash());
    }

    @Test
    void rejectsInconsistentOrIncompleteHistory() {
        PreflopAllInSpot spot = ValidationSpot.create();
        var missingFold = new ArrayList<>(spot.priorActions());
        missingFold.remove(7);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopAllInSpot(
                                spot.id(),
                                100,
                                0.5,
                                spot.firstSeat(),
                                spot.secondSeat(),
                                missingFold,
                                spot.firstRange(),
                                spot.secondRange()));

        var invalidRaise = new ArrayList<>(spot.priorActions());
        invalidRaise.set(
                9,
                new PreflopAllInSpot.Action(
                        spot.secondSeat(), PreflopAllInSpot.ActionKind.RAISE_TO, 21));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopAllInSpot(
                                spot.id(),
                                100,
                                0.5,
                                spot.firstSeat(),
                                spot.secondSeat(),
                                invalidRaise,
                                spot.firstRange(),
                                spot.secondRange()));

        var outOfTurn = new ArrayList<>(spot.priorActions());
        Collections.swap(outOfTurn, 3, 4);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopAllInSpot(
                                spot.id(),
                                100,
                                0.5,
                                spot.firstSeat(),
                                spot.secondSeat(),
                                outOfTurn,
                                spot.firstRange(),
                                spot.secondRange()));

        var underMinimumRaise = new ArrayList<>(spot.priorActions());
        underMinimumRaise.set(
                5,
                new PreflopAllInSpot.Action(
                        spot.secondSeat(), PreflopAllInSpot.ActionKind.RAISE_TO, 4));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new PreflopAllInSpot(
                                spot.id(),
                                100,
                                0.5,
                                spot.firstSeat(),
                                spot.secondSeat(),
                                underMinimumRaise,
                                spot.firstRange(),
                                spot.secondRange()));
    }
}
