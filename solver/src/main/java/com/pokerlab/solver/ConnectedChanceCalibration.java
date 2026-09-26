package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Searches tiny public-card menus for lower exact-check-down error on the research fixture. This is
 * in-sample calibration against four synthetic matchups, not evidence that the selected menu
 * generalizes to other ranges or betting strategies.
 */
public final class ConnectedChanceCalibration {
    public record Selection(
            List<List<Card>> flops,
            List<Card> turns,
            ConnectedChanceAudit.Report audit,
            int acceptedCandidates,
            int rejectedCandidates) {}

    private ConnectedChanceCalibration() {}

    public static Selection search(long seed, int candidates, ExactPreflopEquityOracle oracle) {
        if (candidates < 1 || candidates > 500)
            throw new IllegalArgumentException("Search requires 1-500 candidate menus");
        Objects.requireNonNull(oracle, "oracle");
        SplittableRandom random = new SplittableRandom(seed);
        List<Card> deck = new Deck().cards();
        Selection best = null;
        int accepted = 0;
        int rejected = 0;
        while (accepted < candidates) {
            if (rejected > candidates * 20)
                throw new IllegalStateException("Too many invalid chance menus");
            List<List<Card>> flops = sampleFlops(deck, random);
            List<Card> turns = sampleTurns(deck, flops, random);
            ButtonBigBlindContinuationGame game;
            try {
                game = ButtonBigBlindResearchFixture.create(flops, turns);
            } catch (IllegalArgumentException exception) {
                if (!invalidSampledMenu(exception)) throw exception;
                rejected++;
                continue;
            }
            ConnectedChanceAudit.Report audit = ConnectedChanceAudit.assess(game, oracle);
            accepted++;
            if (best == null || better(audit, best.audit()))
                best = new Selection(flops, turns, audit, accepted, rejected);
        }
        return new Selection(best.flops(), best.turns(), best.audit(), accepted, rejected);
    }

    private static boolean better(
            ConnectedChanceAudit.Report candidate, ConnectedChanceAudit.Report incumbent) {
        int maximum =
                Double.compare(
                        candidate.maxAbsoluteDealErrorBb(), incumbent.maxAbsoluteDealErrorBb());
        return maximum < 0
                || maximum == 0
                        && candidate.meanAbsoluteErrorBb() < incumbent.meanAbsoluteErrorBb();
    }

    private static boolean invalidSampledMenu(IllegalArgumentException exception) {
        String reason = String.valueOf(exception.getMessage());
        return reason.contains("impossible under the conditioned ranges")
                || reason.contains("Every legal joint deal needs")
                || reason.contains("no legal turn candidate");
    }

    private static List<List<Card>> sampleFlops(List<Card> deck, SplittableRandom random) {
        Set<List<Card>> unique = new HashSet<>();
        while (unique.size() < 4) {
            List<Card> cards = sample(deck, 3, random);
            unique.add(cards.stream().sorted(Comparator.comparing(Card::compact)).toList());
        }
        return unique.stream()
                .sorted(
                        Comparator.comparing(
                                cards ->
                                        cards.stream()
                                                .map(Card::compact)
                                                .reduce("", String::concat)))
                .toList();
    }

    private static List<Card> sampleTurns(
            List<Card> deck, List<List<Card>> flops, SplittableRandom random) {
        Set<Card> onFlops = new HashSet<>();
        flops.forEach(onFlops::addAll);
        List<Card> available = deck.stream().filter(card -> !onFlops.contains(card)).toList();
        return sample(available, 4, random).stream()
                .sorted(Comparator.comparing(Card::compact))
                .toList();
    }

    private static List<Card> sample(List<Card> source, int count, SplittableRandom random) {
        List<Card> available = new ArrayList<>(source);
        List<Card> chosen = new ArrayList<>(count);
        for (int index = 0; index < count; index++)
            chosen.add(available.remove(random.nextInt(available.size())));
        return List.copyOf(chosen);
    }
}
