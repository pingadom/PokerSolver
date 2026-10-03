package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SixMaxPolicyFlopTransitionTest {
    private static final List<PublicAction> HISTORY =
            List.of(
                    new PublicAction(Seat.UTG, "fold"), new PublicAction(Seat.HJ, "fold"),
                    new PublicAction(Seat.CO, "fold"), new PublicAction(Seat.BTN, "raise:3.0"),
                    new PublicAction(Seat.SB, "fold"), new PublicAction(Seat.BB, "call"));

    private static WeightedCombo combo(String cards, double weight) {
        var split = cards.split(" ");
        return new WeightedCombo(Card.parse(split[0]), Card.parse(split[1]), weight);
    }

    private static List<List<WeightedCombo>> ranges() {
        return List.of(
                List.of(combo("As Ah", 1), combo("Ac Ad", 3)),
                List.of(combo("Ks Kh", 1)),
                List.of(combo("Qs Qh", 1)),
                List.of(combo("Js Jh", 1)),
                List.of(combo("Ts Th", 1)),
                List.of(combo("9s 9h", 1)));
    }

    private static SixMaxPreflopCheckdownGame game(
            List<List<WeightedCombo>> ranges, CashRakeRule rake) {
        return new SixMaxPreflopCheckdownGame(
                new SixMaxPreflopBetting.Rules(100, 0.5, List.of(3.0, 100.0)),
                ranges,
                rake,
                (hands, mask) -> {
                    double[] shares = new double[6];
                    for (int i = 0; i < 6; i++)
                        if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                    return MultiwayShowdownEstimate.certain(shares);
                });
    }

    /** Policy only for the supplied observed path; no implicit strategy completion. */
    private static CfrSolution policy(SixMaxPreflopCheckdownGame game, boolean rare) {
        Map<String, Map<String, Double>> strategy = new LinkedHashMap<>();
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var state = outcome.state();
            for (var action : HISTORY) {
                Map<String, Double> weights = new LinkedHashMap<>();
                double selected =
                        rare
                                ? 1e-200
                                : action.seat() == Seat.UTG
                                        ? (game.dealtHands(state).getFirst().key().equals("Ah As")
                                                ? 0.25
                                                : 0.75)
                                        : 1;
                String other =
                        game.legalActions(state).stream()
                                .filter(a -> !a.equals(action.action()))
                                .findFirst()
                                .orElseThrow();
                for (String legal : game.legalActions(state))
                    weights.put(
                            legal,
                            legal.equals(action.action())
                                    ? selected
                                    : legal.equals(other) ? 1 - selected : 0.0);
                strategy.put(game.currentPlayer(state) + ":" + game.informationSet(state), weights);
                state = game.afterAction(state, action.action());
            }
        }
        return new CfrSolution(1, strategy);
    }

    private static SixMaxPolicyFlopTransition transition() {
        var game = game(ranges(), CashRakeRule.none());
        return new SixMaxPolicyFlopTransition(game, policy(game, false), HISTORY);
    }

    @Test
    void derivesAllSixPosteriorHandsFromObservedPolicyIncludingTheFoldLikelihood() {
        var transition = transition();
        assertEquals(0.625, transition.reachProbability(), 1e-12);
        assertEquals(0.1, transition.marginal(Seat.UTG).get("Ah As"), 1e-12);
        assertEquals(0.9, transition.marginal(Seat.UTG).get("Ac Ad"), 1e-12);
        assertEquals(Seat.BB, transition.firstToAct());
        assertEquals(Seat.BTN, transition.secondToAct());
        assertEquals(6.5, transition.potBb());
        assertEquals(97, transition.remainingStackBb());
        assertEquals(6, transition.deals().getFirst().hands().size());
        assertEquals(HISTORY, transition.history());
        assertThrows(UnsupportedOperationException.class, () -> transition.deals().clear());
    }

    @Test
    void foldedCardsChangeFlopChanceAndThePosteriorRatherThanBeingReturnedToTheDeck() {
        var transition = transition();
        var flop = List.of(Card.parse("As"), Card.parse("2d"), Card.parse("3d"));
        assertEquals(0.9 / 9_880, transition.flopProbability(flop), 1e-15);
        var conditioned = transition.conditionOnFlop(flop);
        assertEquals(1, conditioned.deals().size());
        assertEquals("Ac Ad", conditioned.deals().getFirst().hands().getFirst().key());
        assertEquals(1, conditioned.deals().getFirst().probability());
        assertEquals(37, conditioned.undealtCards(0).size());
        assertFalse(conditioned.undealtCards(0).contains(Card.parse("Ac")));
        assertFalse(conditioned.undealtCards(0).contains(Card.parse("Ts")));
        assertFalse(conditioned.undealtCards(0).contains(Card.parse("As")));
        var impossible = List.of(Card.parse("As"), Card.parse("Ac"), Card.parse("2d"));
        assertEquals(0, transition.flopProbability(impossible));
        assertThrows(IllegalArgumentException.class, () -> transition.conditionOnFlop(impossible));
    }

    @Test
    void probabilitiesOverEveryPhysicalUnorderedFlopSumToOne() {
        var transition = transition();
        var deck = new Deck().cards();
        double total = 0;
        for (int a = 0; a < 50; a++)
            for (int b = a + 1; b < 51; b++)
                for (int c = b + 1; c < 52; c++)
                    total +=
                            transition.flopProbability(
                                    List.of(deck.get(a), deck.get(b), deck.get(c)));
        assertEquals(1, total, 1e-10);
    }

    @Test
    void sampledFlopsAndRunoutsAreRepeatableAndRespectAllSixHands() {
        var transition = transition();
        for (long seed = 0; seed < 100; seed++) {
            var flop = transition.sampleFlop(seed);
            assertEquals(flop.board(), transition.sampleFlop(seed).board());
            assertEquals(flop.deals(), transition.sampleFlop(seed).deals());
            var runout = flop.sampleRunout(seed + 711);
            assertEquals(runout, flop.sampleRunout(seed + 711));
            var cards = new HashSet<>(runout.board());
            assertEquals(5, cards.size());
            for (var hand : runout.deal().hands()) {
                assertTrue(cards.add(hand.first()));
                assertTrue(cards.add(hand.second()));
            }
            assertEquals(17, cards.size());
        }
    }

    @Test
    void exactFlopBaselineEnumerates666PairsPerDealAndKeepsFoldedChipLosses() {
        var flop =
                transition()
                        .conditionOnFlop(
                                List.of(Card.parse("2c"), Card.parse("3c"), Card.parse("4d")));
        var value = flop.exactCheckdown();
        assertEquals(2 * 666, value.runouts());
        assertEquals(
                1, value.shares().values().stream().mapToDouble(Double::doubleValue).sum(), 1e-12);
        assertEquals(
                0,
                value.utilitiesBb().values().stream().mapToDouble(Double::doubleValue).sum(),
                1e-12);
        assertEquals(-0.5, value.utilitiesBb().get(Seat.SB));
        assertEquals(0, value.utilitiesBb().get(Seat.UTG));
        assertEquals(
                6.5 * value.shares().get(Seat.BB) - 3, value.utilitiesBb().get(Seat.BB), 1e-12);
    }

    @Test
    void anUnbeatableFloppedRoyalFlushWinsEveryExactRunout() {
        var ranges = new ArrayList<>(ranges());
        ranges.set(0, List.of(combo("2c 2d", 1)));
        ranges.set(1, List.of(combo("8c 8d", 1)));
        ranges.set(2, List.of(combo("7c 7d", 1)));
        ranges.set(3, List.of(combo("As Ks", 1)));
        ranges.set(4, List.of(combo("6c 6d", 1)));
        var game = game(ranges, CashRakeRule.none());
        var transition = new SixMaxPolicyFlopTransition(game, policy(game, false), HISTORY);
        var value =
                transition
                        .conditionOnFlop(
                                List.of(Card.parse("Qs"), Card.parse("Js"), Card.parse("Ts")))
                        .exactCheckdown();
        assertEquals(666, value.runouts());
        assertEquals(1, value.shares().get(Seat.BTN));
        assertEquals(3.5, value.utilitiesBb().get(Seat.BTN));
        assertEquals(-3, value.utilitiesBb().get(Seat.BB));
    }

    @Test
    void rejectsBadHistoriesMissingStrategiesZeroReachRakeAndInvalidFlops() {
        var game = game(ranges(), CashRakeRule.none());
        var policy = policy(game, false);
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxPolicyFlopTransition(game, policy, HISTORY.subList(0, 5)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SixMaxPolicyFlopTransition(
                                game, policy, List.of(new PublicAction(Seat.BB, "call"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxPolicyFlopTransition(game, new CfrSolution(1, Map.of()), HISTORY));
        var zeros = new HashMap<>(policy.strategy());
        for (var outcome : game.chanceOutcomes(game.initialState())) {
            var state = outcome.state();
            var map = new LinkedHashMap<>(policy.at(0, game.informationSet(state)));
            map.replaceAll((action, value) -> action.equals("call") ? 1.0 : 0.0);
            zeros.put("0:" + game.informationSet(state), map);
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxPolicyFlopTransition(game, new CfrSolution(1, zeros), HISTORY));
        var raked = game(ranges(), new CashRakeRule(.05, 1, true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SixMaxPolicyFlopTransition(raked, policy(raked, false), HISTORY));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        transition()
                                .flopProbability(
                                        List.of(
                                                Card.parse("2c"),
                                                Card.parse("2c"),
                                                Card.parse("3d"))));
    }

    @Test
    void retainsJointLiveHandCorrelationsRatherThanMultiplyingMarginals() {
        var ranges = new ArrayList<>(ranges());
        ranges.set(3, List.of(combo("Js Jh", 1), combo("9s 8c", 1)));
        ranges.set(5, List.of(combo("9s 9h", 1), combo("8s 8h", 1)));
        var game = game(ranges, CashRakeRule.none());
        var transition = new SixMaxPolicyFlopTransition(game, policy(game, false), HISTORY);
        assertEquals(
                6, transition.deals().size()); // Two folded UTG hands × three legal live pairs.
        assertTrue(transition.marginal(Seat.BTN).get("8c 9s") > 0);
        assertTrue(transition.marginal(Seat.BB).get("9h 9s") > 0);
        for (var deal : transition.deals())
            assertFalse(deal.hands().get(3).conflictsWith(deal.hands().get(5)));
    }

    @Test
    void preservesEmpiricalPrivateChanceLabellingWhenEnumeratingPhysicalPublicCards() {
        var sample = SixMaxJointDealSampler.sample(ranges(), 100, 100, 711);
        var game =
                SixMaxPreflopCheckdownGame.fromSampledDeals(
                        new SixMaxPreflopBetting.Rules(100, .5, List.of(3.0, 100.0)),
                        sample,
                        CashRakeRule.none(),
                        (hands, mask) -> {
                            double[] shares = new double[6];
                            for (int i = 0; i < 6; i++)
                                if ((mask & 1 << i) != 0) shares[i] = 1.0 / Integer.bitCount(mask);
                            return MultiwayShowdownEstimate.certain(shares);
                        });
        var transition = new SixMaxPolicyFlopTransition(game, policy(game, false), HISTORY);
        assertEquals(
                SixMaxPreflopCheckdownGame.ChanceModel.EMPIRICAL_JOINT_DEALS,
                transition.chanceModel());
        assertEquals(37, transition.sampleFlop(711).undealtCards(0).size());
    }

    @Test
    void rareHistoryConditioningRetainsPrecisionInLogSpace() {
        var game = game(ranges(), CashRakeRule.none());
        var transition = new SixMaxPolicyFlopTransition(game, policy(game, true), HISTORY);
        assertEquals(6 * Math.log(1e-200), transition.logReachProbability(), 1e-10);
        assertEquals(0, transition.reachProbability());
        assertEquals(0.25, transition.marginal(Seat.UTG).get("Ah As"), 1e-12);
        assertEquals(0.75, transition.marginal(Seat.UTG).get("Ac Ad"), 1e-12);
    }
}
