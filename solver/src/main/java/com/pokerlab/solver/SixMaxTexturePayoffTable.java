package com.pokerlab.solver;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.pokerlab.core.card.Card;
import com.pokerlab.core.card.Deck;
import com.pokerlab.core.hand.HandEvaluator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
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
        if (dealt.size() != 6 || deckList.size() < 5 || deckList.size() > 40)
            throw new IllegalArgumentException(
                    "Expected six hands and five to forty undealt cards");
        var unique = new HashSet<Card>();
        for (var hand : dealt)
            if (!unique.add(hand.first()) || !unique.add(hand.second()))
                throw new IllegalArgumentException("Overlapping private cards");
        for (var card : deckList)
            if (!unique.add(card)) throw new IllegalArgumentException("Overlapping deck cards");
        Card[] deck = deckList.toArray(Card[]::new);
        int n = deck.length;
        byte[] textures = new byte[n * n * n];
        long[] flopCounts = new long[6], observedRunouts = new long[6];
        for (int a = 0; a < n - 2; a++)
            for (int b = a + 1; b < n - 1; b++)
                for (int c = b + 1; c < n; c++) {
                    int texture = classifyIndex(deck[a], deck[b], deck[c]);
                    textures[(a * n + b) * n + c] = (byte) texture;
                    flopCounts[texture]++;
                }
        int[] masks = new int[15], first = new int[15], second = new int[15];
        int pairIndex = 0;
        for (int mask = 0; mask < 64; mask++)
            if (Integer.bitCount(mask) == 2) {
                masks[pairIndex] = mask;
                first[pairIndex] = Integer.numberOfTrailingZeros(mask);
                second[pairIndex] =
                        Integer.numberOfTrailingZeros(mask ^ Integer.lowestOneBit(mask));
                pairIndex++;
            }
        long[][] wins = new long[15][6], ties = new long[15][6];
        Card[][] hands = new Card[6][7];
        int[] scores = new int[6], indices = new int[5], frequencies = new int[6];
        for (int seat = 0; seat < 6; seat++) {
            hands[seat][0] = dealt.get(seat).first();
            hands[seat][1] = dealt.get(seat).second();
        }
        for (int a = 0; a < n - 4; a++)
            for (int b = a + 1; b < n - 3; b++)
                for (int c = b + 1; c < n - 2; c++)
                    for (int d = c + 1; d < n - 1; d++)
                        for (int e = d + 1; e < n; e++) {
                            indices[0] = a;
                            indices[1] = b;
                            indices[2] = c;
                            indices[3] = d;
                            indices[4] = e;
                            Arrays.fill(frequencies, 0);
                            // Each five-card board represents ten equally likely flop subsets.
                            for (int x = 0; x < 3; x++)
                                for (int y = x + 1; y < 4; y++)
                                    for (int z = y + 1; z < 5; z++)
                                        frequencies[
                                                textures[
                                                        (indices[x] * n + indices[y]) * n
                                                                + indices[z]]]++;
                            for (int t = 0; t < 6; t++) observedRunouts[t] += frequencies[t];
                            for (int seat = 0; seat < 6; seat++) {
                                for (int card = 0; card < 5; card++)
                                    hands[seat][card + 2] = deck[indices[card]];
                                scores[seat] = HandEvaluator.evaluateBestScore(hands[seat]);
                            }
                            for (int p = 0; p < 15; p++) {
                                int comparison =
                                        Integer.compare(scores[first[p]], scores[second[p]]);
                                if (comparison < 0) continue;
                                for (int t = 0; t < 6; t++)
                                    if (comparison == 0) ties[p][t] += frequencies[t];
                                    else wins[p][t] += frequencies[t];
                            }
                        }
        long perFlop = (long) (n - 3) * (n - 4) / 2;
        for (int t = 0; t < 6; t++)
            if (observedRunouts[t] != flopCounts[t] * perFlop)
                throw new IllegalStateException("Flop/runout enumeration identity failed");
        var pairs = new ArrayList<Pair>();
        for (int p = 0; p < 15; p++) pairs.add(new Pair(masks[p], longs(wins[p]), longs(ties[p])));
        return new Deal(dealt.stream().map(WeightedCombo::key).toList(), longs(flopCounts), pairs);
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
