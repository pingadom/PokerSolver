package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import java.util.*;
import org.junit.jupiter.api.Test;

class WeightedRangeNotationTest {
    @Test
    void all169ClassesPartitionEveryOneOf1326PhysicalHands() {
        var labels = WeightedRangeNotation.handClasses();
        assertEquals(169, labels.size());
        assertEquals(169, new HashSet<>(labels).size());
        assertEquals("AA", labels.get(0));
        assertEquals("AKs", labels.get(1));
        assertEquals("AKo", labels.get(13));
        assertEquals("KK", labels.get(14));
        assertEquals("22", labels.getLast());
        Set<String> covered = new HashSet<>();
        for (String label : labels) {
            var range = WeightedRangeNotation.compile(label);
            assertEquals(
                    label.length() == 2 ? 6 : label.endsWith("s") ? 4 : 12, range.combos().size());
            for (var c : range.combos()) {
                assertTrue(covered.add(c.key()));
                char high =
                        "23456789TJQKA"
                                .charAt(
                                        Math.max(
                                                        c.first().rank().value(),
                                                        c.second().rank().value())
                                                - 2);
                char low =
                        "23456789TJQKA"
                                .charAt(
                                        Math.min(
                                                        c.first().rank().value(),
                                                        c.second().rank().value())
                                                - 2);
                assertEquals(label.charAt(0), high);
                assertEquals(label.charAt(1), low);
                if (label.length() == 3)
                    assertEquals(label.endsWith("s"), c.first().suit() == c.second().suit());
                assertEquals(label, WeightedRangeNotation.handClass(c));
            }
        }
        var deck = new ArrayList<String>();
        for (char rank : "23456789TJQKA".toCharArray())
            for (char suit : "cdhs".toCharArray()) deck.add("" + rank + suit);
        Set<String> literal = new HashSet<>();
        for (int a = 0; a < 52; a++)
            for (int b = a + 1; b < 52; b++) {
                var cards = new ArrayList<>(List.of(deck.get(a), deck.get(b)));
                Collections.sort(cards);
                literal.add(String.join(" ", cards));
            }
        assertEquals(1326, covered.size());
        assertEquals(literal, covered);
    }

    @Test
    void wildcardHasFullDeckSupportWithoutGivingAClassEqualPriorMass() {
        var r = WeightedRangeNotation.compile("*");
        assertEquals(1326, r.combos().size());
        assertEquals(1326, r.weightMass());
        assertEquals(6, r.coverage().get("AA").weightMass());
        assertEquals(4, r.coverage().get("AKs").weightMass());
        assertEquals(12, r.coverage().get("AKo").weightMass());
    }

    @Test
    void plusKeepsTheHighRankFixedForNonPairs() {
        assertEquals(30, WeightedRangeNotation.compile("TT+").combos().size());
        assertEquals(36, WeightedRangeNotation.compile("A5s+").combos().size());
        assertEquals(12, WeightedRangeNotation.compile("KTs+").combos().size());
        assertEquals(4, WeightedRangeNotation.compile("76s+").combos().size());
        assertEquals(12, WeightedRangeNotation.compile("AKo+").combos().size());
    }

    @Test
    void intervalsAreInclusiveInEitherDirectionAndNeverInventDiagonalClasses() {
        assertEquals(
                WeightedRangeNotation.compile("TT,JJ,QQ").combos(),
                WeightedRangeNotation.compile("QQ-TT").combos());
        assertEquals(
                WeightedRangeNotation.compile("A2s,A3s,A4s,A5s").combos(),
                WeightedRangeNotation.compile("A5s-A2s").combos());
        assertEquals(12, WeightedRangeNotation.compile("KTs-KQs").combos().size());
        for (String bad : List.of("A5s-K5s", "A2s-A5o", "AA-AKs", "AKs--AQs"))
            assertThrows(IllegalArgumentException.class, () -> WeightedRangeNotation.compile(bad));
    }

    @Test
    void weightsApplyToEachPhysicalComboAndExactCombosMayBePartialClasses() {
        var r = WeightedRangeNotation.compile("AA@0.5,AKs,AKo@0.25");
        assertEquals(22, r.combos().size());
        assertEquals(10, r.weightMass());
        assertEquals(3, r.coverage().get("AA").weightMass());
        assertEquals(4, r.coverage().get("AKs").weightMass());
        assertEquals(3, r.coverage().get("AKo").weightMass());
        var partial = WeightedRangeNotation.compile("AsKh@0.5,AcKd");
        assertEquals(2, partial.combos().size());
        assertEquals(1.5, partial.weightMass());
        assertEquals(2, partial.coverage().get("AKo").physicalCombos());
    }

    @Test
    void semanticHashAndOrderingIgnoreEquivalentInputSpellingAndTermOrder() {
        var a = WeightedRangeNotation.compile("TT+,AKs@0.5");
        var b = WeightedRangeNotation.compile(" AKs @ 0.5, AA@1,KK,QQ,JJ,TT ");
        assertEquals(a.combos(), b.combos());
        assertEquals(a.contentHash(), b.contentHash());
        assertEquals(64, a.contentHash().length());
        assertNotEquals(a.contentHash(), WeightedRangeNotation.compile("TT+,AKs").contentHash());
        assertEquals(
                WeightedRangeNotation.compile("AsKh").contentHash(),
                WeightedRangeNotation.compile("KhAs").contentHash());
        assertEquals(
                a.combos().stream().map(WeightedCombo::key).sorted().toList(),
                a.combos().stream().map(WeightedCombo::key).toList());
    }

    @Test
    void allPhysicalOverlapsRejectInsteadOfSummingOrClampingWeights() {
        for (String bad :
                List.of("AA,AA", "QQ+,AA", "AKs,AcKc", "AsKh,KhAs", "*,AA", "A5s-A2s,A3s@0.5")) {
            var error =
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> WeightedRangeNotation.compile(bad));
            assertTrue(error.getMessage().contains("Overlapping"));
        }
    }

    @Test
    void malformedAndAmbiguousTokensNeverBecomeRanges() {
        for (String bad :
                List.of(
                        "",
                        ",AA",
                        "AA,",
                        "AK",
                        "AKx",
                        "aa",
                        "KAs",
                        "10Ts",
                        "AsAs",
                        "Ac Kc",
                        "AA@@0.5",
                        "AA@",
                        "AA@0",
                        "AA@-1",
                        "AA@NaN",
                        "AA@Infinity",
                        "AA@0x1p0",
                        "AA@0.5e0",
                        "AA@25%",
                        "+",
                        "AA++",
                        "AKs-AQs-AJs"))
            assertThrows(
                    IllegalArgumentException.class, () -> WeightedRangeNotation.compile(bad), bad);
        assertThrows(IllegalArgumentException.class, () -> WeightedRangeNotation.compile(null));
    }

    @Test
    void decimalBoundsRejectValuesThatWouldRoundDownToOneAndUnderflowToZero() {
        for (String bad :
                List.of(
                        "AA@1.0000000000000000000000001",
                        "AA@0." + "0".repeat(400) + "1",
                        "AA@0.000"))
            assertThrows(IllegalArgumentException.class, () -> WeightedRangeNotation.compile(bad));
        assertEquals(3, WeightedRangeNotation.compile("AA@0.50000000000000000001").weightMass());
    }

    @Test
    void inputAndTermCapsAreEnforcedBeforeUnboundedExpansion() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WeightedRangeNotation.compile(
                                " ".repeat(WeightedRangeNotation.MAX_CHARACTERS + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        WeightedRangeNotation.compile(
                                String.join(
                                        ",",
                                        Collections.nCopies(
                                                WeightedRangeNotation.MAX_TERMS + 1, "AcKd"))));
    }

    @Test
    void blockerCountsAndMassesRemainUnnormalizedAndRetainExactWeights() {
        var r = WeightedRangeNotation.compile("AA@0.5,AKs,AKo@0.25");
        var c = r.conditionOnDeadCards(List.of(Card.parse("Ac"), Card.parse("Kd")));
        assertEquals(22, c.inputComboCount());
        assertEquals(10, c.blockedComboCount());
        assertEquals(12, c.combos().size());
        assertEquals(10, c.inputWeightMass());
        assertEquals(4.75, c.blockedWeightMass());
        assertEquals(5.25, c.retainedWeightMass());
        assertTrue(c.hasSupport());
        for (var combo : c.combos()) {
            assertFalse(List.of(combo.first(), combo.second()).contains(Card.parse("Ac")));
            assertFalse(List.of(combo.first(), combo.second()).contains(Card.parse("Kd")));
            assertTrue(r.combos().contains(combo));
        }
        assertEquals(WeightedRangeNotation.WEIGHT_SCOPE, c.weightScope());
    }

    @Test
    void literalFullDeckBlockerCountsMatchChooseRemainingCardsTwo() {
        var full = WeightedRangeNotation.compile("*");
        var deck = new ArrayList<Card>();
        for (char rank : "23456789TJQKA".toCharArray())
            for (char suit : "cdhs".toCharArray()) deck.add(Card.parse("" + rank + suit));
        for (int n = 0; n <= 52; n++) {
            var c = full.conditionOnDeadCards(deck.subList(0, n));
            int expected = (52 - n) * (51 - n) / 2;
            assertEquals(expected, c.combos().size());
            assertEquals(1326 - expected, c.blockedComboCount());
        }
    }

    @Test
    void allBlockedSupportIsExplicitAndCannotBePassedAsAUsableRange() {
        var c =
                WeightedRangeNotation.compile("AA")
                        .conditionOnDeadCards(
                                List.of(
                                        Card.parse("Ac"),
                                        Card.parse("Ad"),
                                        Card.parse("Ah"),
                                        Card.parse("As")));
        assertFalse(c.hasSupport());
        assertTrue(c.combos().isEmpty());
        assertEquals(0, c.retainedWeightMass());
        assertEquals(6, c.blockedWeightMass());
        assertThrows(IllegalArgumentException.class, c::requireSupportedCombos);
    }

    @Test
    void knownCardInputsAndAllReturnedCollectionsAreDefensive() {
        var r = WeightedRangeNotation.compile("AA");
        var cards = new ArrayList<>(List.of(Card.parse("Ac")));
        var c = r.conditionOnDeadCards(cards);
        cards.clear();
        assertEquals(3, c.combos().size());
        assertThrows(
                IllegalArgumentException.class,
                () -> r.conditionOnDeadCards(List.of(Card.parse("Ac"), Card.parse("Ac"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> r.conditionOnDeadCards(Collections.singletonList(null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> r.conditionOnDeadCards(Collections.nCopies(53, Card.parse("Ac"))));
        assertThrows(IllegalArgumentException.class, () -> r.conditionOnDeadCards(null));
        assertThrows(UnsupportedOperationException.class, () -> r.combos().clear());
        assertThrows(UnsupportedOperationException.class, () -> r.coverage().clear());
        assertThrows(
                UnsupportedOperationException.class,
                () -> WeightedRangeNotation.handClasses().clear());
        assertThrows(UnsupportedOperationException.class, () -> c.combos().clear());
        assertSame(c.combos(), c.requireSupportedCombos());
    }
}
