package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.util.List;

/** Compares check-down showdown equity under the declared turn deck and all legal turn cards. */
public final class FlopTurnChanceAudit {
    public record Report(
            double exactCheckdownBb,
            double restrictedCheckdownBb,
            double weightedErrorBb,
            double maxDealErrorBb,
            int legalDeals,
            int exactTurnCardsPerDeal,
            int riverCardsPerTurn) {}

    private FlopTurnChanceAudit() {}

    public static Report assess(FlopTurnRiverSpot spot) {
        double totalWeight = 0;
        double exact = 0;
        double restricted = 0;
        double maxError = 0;
        int deals = 0;
        for (WeightedCombo first : spot.firstRange()) {
            for (WeightedCombo second : spot.secondRange()) {
                if (first.conflictsWith(second)) continue;
                List<Card> fullTurns = remaining(spot.flop(), first, second, null);
                List<Card> sampledTurns =
                        spot.turnCandidates().stream()
                                .filter(card -> !FlopTurnRiverSpot.blocked(card, first, second))
                                .toList();
                double fullEquity = equity(spot.flop(), first, second, fullTurns);
                double sampledEquity = equity(spot.flop(), first, second, sampledTurns);
                double weight = first.weight() * second.weight();
                exact += weight * fullEquity;
                restricted += weight * sampledEquity;
                maxError =
                        Math.max(maxError, Math.abs(fullEquity - sampledEquity) * spot.potBb() / 2);
                totalWeight += weight;
                deals++;
            }
        }
        exact = exact / totalWeight * spot.potBb() / 2;
        restricted = restricted / totalWeight * spot.potBb() / 2;
        return new Report(exact, restricted, restricted - exact, maxError, deals, 45, 44);
    }

    private static double equity(
            List<Card> flop, WeightedCombo first, WeightedCombo second, List<Card> turns) {
        double sum = 0;
        for (Card turn : turns) {
            List<Card> rivers = remaining(flop, first, second, turn);
            for (Card river : rivers) {
                int firstScore =
                        HandEvaluator.evaluateBestScore(
                                first.first(),
                                first.second(),
                                flop.get(0),
                                flop.get(1),
                                flop.get(2),
                                turn,
                                river);
                int secondScore =
                        HandEvaluator.evaluateBestScore(
                                second.first(),
                                second.second(),
                                flop.get(0),
                                flop.get(1),
                                flop.get(2),
                                turn,
                                river);
                sum += Integer.compare(firstScore, secondScore) / (double) rivers.size();
            }
        }
        return sum / turns.size();
    }

    private static List<Card> remaining(
            List<Card> flop, WeightedCombo first, WeightedCombo second, Card turn) {
        Deck deck = new Deck();
        flop.forEach(deck::remove);
        deck.remove(first.first());
        deck.remove(first.second());
        deck.remove(second.first());
        deck.remove(second.second());
        if (turn != null) deck.remove(turn);
        return deck.cards();
    }
}
