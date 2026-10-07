package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.function.IntConsumer;

/** Exact full-deck pair equities given a coarse public flop texture, including folded blockers. */
public final class SixMaxTexturePayoffTable {
    public static final String SCHEMA = "six-max-texture-payoff-table/v1";
    public static final String CLASSIFIER = "RANK_SUIT_MULTIPLICITY/v1";
    public static final int RUNOUTS_PER_FLOP =
            666; // Choose 2 from 37 after twelve hole cards and flop.

    public enum Texture {
        DISTINCT_MONOTONE,
        DISTINCT_TWO_TONE,
        DISTINCT_RAINBOW,
        PAIRED_TWO_TONE,
        PAIRED_RAINBOW,
        TRIPS
    }

    public record Pair(int activeMask, List<Long> firstWins, List<Long> ties) {
        public Pair {
            if ((activeMask & ~63) != 0 || Integer.bitCount(activeMask) != 2)
                throw new IllegalArgumentException("Pair must contain two of six seats");
            firstWins = counts(firstWins);
            ties = counts(ties);
        }

        public double share(int player, int texture, long runouts) {
            if (player < 0
                    || player >= 6
                    || texture < 0
                    || texture >= 6
                    || (activeMask & (1 << player)) == 0
                    || runouts <= 0)
                throw new IllegalArgumentException(
                        "Expected an active player and positive runouts");
            if (firstWins.get(texture) > runouts
                    || ties.get(texture) > runouts - firstWins.get(texture))
                throw new IllegalArgumentException("Pair counts exceed the supplied runout total");
            double first = (firstWins.get(texture) + .5 * ties.get(texture)) / runouts;
            return player == Integer.numberOfTrailingZeros(activeMask) ? first : 1 - first;
        }
    }

    public record Deal(List<String> hands, List<Long> flopCounts, List<Pair> pairs) {
        public Deal {
            hands = List.copyOf(hands);
            if (hands.size() != 6)
                throw new IllegalArgumentException("Expected six physical hands");
            flopCounts = counts(flopCounts);
            pairs = List.copyOf(pairs);
            if (pairs.size() != 15 || pairs.stream().map(Pair::activeMask).distinct().count() != 15)
                throw new IllegalArgumentException("Expected all fifteen unique active pairs");
        }

        public Pair pair(int mask) {
            return pairs.stream()
                    .filter(pair -> pair.activeMask() == mask)
                    .findFirst()
                    .orElseThrow();
        }
    }

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String classifier,
            String sourcePackHash,
            String sourceSpotHash,
            List<Deal> deals) {
        public Artifact {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !CLASSIFIER.equals(classifier))
                throw new IllegalArgumentException("Unsupported texture table identity");
            for (String hash : List.of(sourcePackHash, sourceSpotHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid source hash");
            deals = List.copyOf(deals);
            if (deals.isEmpty() || deals.size() > 12)
                throw new IllegalArgumentException("Texture table supports one to twelve deals");
        }
    }

    private SixMaxTexturePayoffTable() {}

    public static Texture classify(Card a, Card b, Card c) {
        if (a.equals(b) || a.equals(c) || b.equals(c))
            throw new IllegalArgumentException("Flop cards must be distinct");
        return Texture.values()[classifyIndex(a, b, c)];
    }

    private static int classifyIndex(Card a, Card b, Card c) {
        boolean pair = a.rank() == b.rank() || a.rank() == c.rank() || b.rank() == c.rank();
        boolean triple = a.rank() == b.rank() && b.rank() == c.rank();
        boolean mono = a.suit() == b.suit() && b.suit() == c.suit();
        boolean rainbow = a.suit() != b.suit() && a.suit() != c.suit() && b.suit() != c.suit();
        return triple ? 5 : pair ? rainbow ? 4 : 3 : mono ? 0 : rainbow ? 2 : 1;
    }

    /** Production always enumerates all forty undealt cards and all 658,008 five-card boards. */
    public static Artifact generate(SixMaxPreflopSolutionPack source, IntConsumer progress) {
        Objects.requireNonNull(progress, "progress");
        var game = source.rebuildGame();
        if (game.chanceOutcomes(game.initialState()).size() > 12)
            throw new IllegalArgumentException("Texture generation supports at most twelve deals");
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(source.payoffMethod()))
            throw new IllegalArgumentException(
                    "Exact source payoffs are required for marginal validation");
        var deals = new ArrayList<Deal>();
        for (var root : game.chanceOutcomes(game.initialState())) {
            var hands = game.dealtHands(root.state());
            deals.add(enumerate(hands, undealt(hands)));
            progress.accept(deals.size());
        }
        var artifact =
                new Artifact(
                        SCHEMA,
                        "VALIDATION_ONLY",
                        CLASSIFIER,
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        deals);
        validate(artifact, source);
        return artifact;
    }

    // The small-deck overload permits independent analytic enumeration tests, never production
    // export.
    static Deal enumerate(List<WeightedCombo> dealt, List<Card> deckList) {
        var exact =
                SixMaxConditionalPayoffEnumeration.enumerate(
                        dealt, deckList, 6, SixMaxTexturePayoffTable::classifyIndex);
        return new Deal(
                dealt.stream().map(WeightedCombo::key).toList(),
                exact.flopCounts(),
                exact.pairs().stream()
                        .map(pair -> new Pair(pair.activeMask(), pair.firstWins(), pair.ties()))
                        .toList());
    }

    public static void validate(Artifact artifact, SixMaxPreflopSolutionPack source) {
        if (!artifact.sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source))
                || !artifact.sourceSpotHash().equals(source.spotHash())
                || !MultiwaySolutionPack.EXACT_ENUMERATION.equals(source.payoffMethod()))
            throw new IllegalArgumentException("Texture table must bind the exact source pack");
        var game = source.rebuildGame();
        var roots = game.chanceOutcomes(game.initialState());
        if (artifact.deals().size() != roots.size())
            throw new IllegalArgumentException("Texture table must retain every private world");
        for (int i = 0; i < roots.size(); i++) {
            var hands = game.dealtHands(roots.get(i).state());
            var deal = artifact.deals().get(i);
            if (!deal.hands().equals(hands.stream().map(WeightedCombo::key).toList()))
                throw new IllegalArgumentException("Texture table private support/order differs");
            if (!deal.flopCounts().equals(physicalCounts(hands)))
                throw new IllegalArgumentException(
                        "Texture counts disagree with physical folded blockers");
            for (var pair : deal.pairs()) {
                long totalWins = 0, totalTies = 0;
                for (int t = 0; t < 6; t++) {
                    long runouts = deal.flopCounts().get(t) * RUNOUTS_PER_FLOP;
                    long wins = pair.firstWins().get(t), ties = pair.ties().get(t);
                    if (wins > runouts || ties > runouts - wins)
                        throw new IllegalArgumentException(
                                "Texture wins/ties exceed physical runouts");
                    totalWins += wins;
                    totalTies += ties;
                }
                int mask = pair.activeMask(), firstSeat = Integer.numberOfTrailingZeros(mask);
                var original =
                        source.payoffs().stream()
                                .filter(
                                        row ->
                                                row.activeMask() == mask
                                                        && row.dealtCombos().equals(deal.hands()))
                                .findFirst()
                                .orElseThrow();
                double marginal =
                        (totalWins + .5 * totalTies)
                                / (SixMaxPolicyFlopTransition.FLOPS_PER_DEAL
                                        * (double) RUNOUTS_PER_FLOP);
                if (Math.abs(marginal - original.estimate().shares()[firstSeat]) > 1e-12)
                    throw new IllegalArgumentException(
                            "Texture equities do not recover exact source marginal");
            }
        }
    }

    static List<Long> physicalCounts(List<WeightedCombo> hands) {
        var deck = undealt(hands);
        long[] counts = new long[6];
        for (int a = 0; a < 38; a++)
            for (int b = a + 1; b < 39; b++)
                for (int c = b + 1; c < 40; c++)
                    counts[classifyIndex(deck.get(a), deck.get(b), deck.get(c))]++;
        return longs(counts);
    }

    private static List<Card> undealt(List<WeightedCombo> hands) {
        var deck = new Deck();
        for (var hand : hands)
            if (!deck.remove(hand.first()) || !deck.remove(hand.second()))
                throw new IllegalArgumentException("Private cards overlap");
        return deck.cards();
    }

    private static List<Long> counts(List<Long> values) {
        var result = List.copyOf(values);
        if (result.size() != 6 || result.stream().anyMatch(value -> value < 0))
            throw new IllegalArgumentException("Expected six nonnegative texture counts");
        return result;
    }

    private static List<Long> longs(long[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    static ObjectMapper mapper() {
        return new ObjectMapper()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    }

    public static Artifact read(Path input, SixMaxPreflopSolutionPack source) throws Exception {
        if (Files.size(input) > 4L * 1024 * 1024)
            throw new IllegalArgumentException("Texture table exceeds 4 MiB");
        var result = mapper().readValue(Files.readString(input), Artifact.class);
        validate(result, source);
        return result;
    }

    public static String json(Artifact artifact) throws Exception {
        return mapper().writerWithDefaultPrettyPrinter()
                        .writeValueAsString(artifact)
                        .replace("\r\n", "\n")
                + "\n";
    }

    public static String hash(Artifact artifact) throws Exception {
        return HexFormat.of()
                .formatHex(
                        MessageDigest.getInstance("SHA-256")
                                .digest(
                                        mapper().writeValueAsString(artifact)
                                                .getBytes(StandardCharsets.UTF_8)));
    }
}
