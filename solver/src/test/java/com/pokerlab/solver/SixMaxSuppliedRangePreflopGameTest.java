package com.pokerlab.solver;

import static com.pokerlab.solver.SixMaxSuppliedRangePreflopGame.*;
import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Deck;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxSuppliedRangePreflopGame.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class SixMaxSuppliedRangePreflopGameTest {
    static Path data(String name) {
        return Path.of("../docs/data/" + name).toAbsolutePath().normalize();
    }

    static Input input() throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        Files.readString(data("supplied-range-button-defense-input.json")),
                        Input.class);
    }

    @Test
    void preflightMeasuresExpandedSupportWithoutFullSixSeatSourceProduct() throws Exception {
        var budget = SixMaxSuppliedRangePreflopGame.preflight(input());
        assertEquals(18, budget.jointDeals());
        assertEquals(6, budget.publicStates());
        assertEquals(109, budget.completeTreeStates());
        assertEquals(11844144, budget.enumeratedBoards());
        assertEquals(23688288, budget.handEvaluations());
        assertEquals(Map.of(Seat.BTN, 3, Seat.BB, 2), budget.informationSets());
        assertEquals(Map.of(Seat.BTN, 7, Seat.BB, 7), budget.sequences());
        assertTrue(18L * 11566 > SixMaxPreflopCheckdownGame.MAX_DEAL_PUBLIC_STATES);
        assertThrows(
                UnsupportedOperationException.class, () -> budget.sequences().put(Seat.CO, 10));
    }

    @Test
    void jointInputCanonicalizesOrderWithoutInventingMissingCartesianWorlds() throws Exception {
        var declared = input();
        var a = declared.worlds().getFirst();
        var b =
                declared.worlds().stream()
                        .filter(
                                w ->
                                        !w.dealtCombos().get(3).equals(a.dealtCombos().get(3))
                                                && !w.dealtCombos()
                                                        .get(5)
                                                        .equals(a.dealtCombos().get(5)))
                        .findFirst()
                        .orElseThrow();
        var sparse =
                new Input(
                        INPUT_SCHEMA,
                        declared.specification(),
                        List.of(new World(b.dealtCombos(), 1), new World(a.dealtCombos(), 3)));
        var reversed =
                new Input(INPUT_SCHEMA, declared.specification(), sparse.worlds().reversed());
        assertEquals(sparse, reversed);
        var game = new SixMaxSuppliedRangePreflopGame(sparse);
        assertEquals(2, game.prior().size());
        assertEquals(
                List.of(.25, .75),
                game.prior().stream().map(JointDeal::conditionalProbability).sorted().toList());
        assertEquals(
                sparse.worlds().stream().map(World::dealtCombos).toList(),
                game.prior().stream().map(JointDeal::dealtCombos).toList());
        assertEquals(HISTORY_REACH, game.binding().sourceHistoryReachStatus());
        assertNotEquals(SixMaxHeadsUpPreflopGame.BELIEFS, game.binding().beliefs());
    }

    @Test
    void rejectsOversizedExactWorkBeforeEnumeratingAnyBoards() throws Exception {
        var original = input();
        var world = original.worlds().getFirst();
        var deck = new Deck();
        for (int seat = 0; seat < 6; seat++)
            if (seat != 1) {
                var key = world.dealtCombos().get(seat);
                deck.remove(com.pokerlab.core.card.Card.parse(key.substring(0, 2)));
                deck.remove(com.pokerlab.core.card.Card.parse(key.substring(3)));
            }
        var cards = deck.cards();
        var worlds = new ArrayList<World>();
        for (int i = 1; i <= 31; i++) {
            var hands = new ArrayList<>(world.dealtCombos());
            hands.set(1, cards.get(0).compact() + " " + cards.get(i).compact());
            worlds.add(new World(hands, 1));
        }
        var tooMuch = new Input(INPUT_SCHEMA, original.specification(), worlds);
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopGame.preflight(tooMuch));
        assertThrows(
                IllegalArgumentException.class, () -> new SixMaxSuppliedRangePreflopGame(tooMuch));
        assertEquals(
                30 * 658008L,
                SixMaxSuppliedRangePreflopGame.preflight(
                                new Input(
                                        INPUT_SCHEMA,
                                        original.specification(),
                                        worlds.subList(0, 30)))
                        .enumeratedBoards());
    }

    @Test
    void rejectsInvalidCardsWeightsDuplicatesAndUnderflow() throws Exception {
        var input = input();
        var w = input.worlds().getFirst();
        assertThrows(IllegalArgumentException.class, () -> new World(w.dealtCombos(), Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> new World(w.dealtCombos(), 0));
        var collision = new ArrayList<>(w.dealtCombos());
        collision.set(0, collision.get(5));
        assertThrows(IllegalArgumentException.class, () -> new World(collision, 1));
        var malformed = new ArrayList<>(w.dealtCombos());
        malformed.set(0, "AA");
        assertThrows(IllegalArgumentException.class, () -> new World(malformed, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Input(INPUT_SCHEMA, input.specification(), List.of(w, w)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Input("source-policy", input.specification(), List.of(w)));
        var tiny =
                new Input(
                        INPUT_SCHEMA,
                        input.specification(),
                        List.of(
                                new World(w.dealtCombos(), Double.MIN_VALUE),
                                new World(
                                        input.worlds().getLast().dealtCombos(), Double.MAX_VALUE)));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopGame.preflight(tiny));
        var huge =
                new Input(
                        INPUT_SCHEMA,
                        input.specification(),
                        List.of(
                                new World(w.dealtCombos(), Double.MAX_VALUE),
                                new World(
                                        input.worlds().getLast().dealtCombos(), Double.MAX_VALUE)));
        assertDoesNotThrow(() -> SixMaxSuppliedRangePreflopGame.preflight(huge));
    }

    @Test
    void rejectsOutOfTurnNonHeadsUpAndSingleFutureActorTrees() throws Exception {
        var input = input();
        var spec = input.specification();
        var history = new ArrayList<>(spec.history());
        Collections.swap(history, 0, 1);
        var bad =
                new Input(
                        INPUT_SCHEMA,
                        new SixMaxHeadsUpPreflopGame.Specification(history, spec.rules()),
                        input.worlds());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopGame.preflight(bad));
        var three =
                new Input(
                        INPUT_SCHEMA,
                        new SixMaxHeadsUpPreflopGame.Specification(
                                spec.history().subList(0, 4), spec.rules()),
                        input.worlds());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopGame.preflight(three));
        var rules =
                new SixMaxPreflopBetting.Rules(
                        100, .5, List.of(3.0), SixMaxPreflopBetting.RaiseSchedule.NEXT_TARGET);
        var single =
                new Input(
                        INPUT_SCHEMA,
                        new SixMaxHeadsUpPreflopGame.Specification(spec.history(), rules),
                        input.worlds());
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxSuppliedRangePreflopGame.preflight(single));
    }
}
