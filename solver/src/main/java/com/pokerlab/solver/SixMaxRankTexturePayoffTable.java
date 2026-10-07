package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntConsumer;

/** Exact rank/texture equities. Actual suits and which rank shares a suit remain hidden. */
public final class SixMaxRankTexturePayoffTable {
    public static final String SCHEMA = "six-max-rank-texture-payoffs/v1";
    public static final String CLASSIFIER = "SORTED_BOARD_RANKS_SUIT_MULTIPLICITY/v1";
    public static final int MAX_SIGNALS = 1183;
    public static final int RUNOUTS_PER_FLOP = 666;

    public record Signal(
            int lowRank, int middleRank, int highRank, SixMaxTexturePayoffTable.Texture texture)
            implements Comparable<Signal> {
        public Signal {
            Objects.requireNonNull(texture, "texture");
            if (lowRank < 2 || highRank > 14 || lowRank > middleRank || middleRank > highRank)
                throw new IllegalArgumentException("Sorted flop ranks must be in 2–14");
            boolean trips = lowRank == highRank;
            boolean pair = lowRank == middleRank || middleRank == highRank;
            if (trips
                    ? texture != SixMaxTexturePayoffTable.Texture.TRIPS
                    : pair
                            ? texture != SixMaxTexturePayoffTable.Texture.PAIRED_TWO_TONE
                                    && texture != SixMaxTexturePayoffTable.Texture.PAIRED_RAINBOW
                            : texture.ordinal() >= 3)
                throw new IllegalArgumentException("Rank multiplicity and texture disagree");
        }

        /** Stable public signal encoding, independent of private-world or palette indices. */
        public int key() {
            return (((lowRank - 2) * 13 + middleRank - 2) * 13 + highRank - 2) * 6
                    + texture.ordinal();
        }

        @Override
        public int compareTo(Signal other) {
            return Integer.compare(key(), other.key());
        }

        public static Signal from(Card first, Card second, Card third) {
            int[] ranks = {first.rank().value(), second.rank().value(), third.rank().value()};
            Arrays.sort(ranks);
            return new Signal(
                    ranks[0],
                    ranks[1],
                    ranks[2],
                    SixMaxTexturePayoffTable.classify(first, second, third));
        }
    }

    public record Pair(int activeMask, List<Long> firstWins, List<Long> ties) {
        public Pair {
            if ((activeMask & ~63) != 0 || Integer.bitCount(activeMask) != 2)
                throw new IllegalArgumentException("Pair requires two of six seats");
            firstWins = counts(firstWins);
            ties = counts(ties);
            if (firstWins.size() != ties.size())
                throw new IllegalArgumentException("Pair vector widths differ");
        }

        public double share(int player, int signal, long runouts) {
            if (player < 0
                    || player >= 6
                    || (activeMask & (1 << player)) == 0
                    || signal < 0
                    || signal >= firstWins.size()
                    || runouts <= 0
                    || firstWins.get(signal) > runouts
                    || ties.get(signal) > runouts - firstWins.get(signal))
                throw new IllegalArgumentException(
                        "Invalid active player, signal or runout counts");
            double first = (firstWins.get(signal) + .5 * ties.get(signal)) / runouts;
            return player == Integer.numberOfTrailingZeros(activeMask) ? first : 1 - first;
        }
    }

    public record Deal(List<String> hands, List<Long> flopCounts, List<Pair> pairs) {
        public Deal {
            hands = List.copyOf(hands);
            flopCounts = counts(flopCounts);
            pairs = List.copyOf(pairs);
            int width = flopCounts.size();
            if (hands.size() != 6
                    || pairs.size() != 15
                    || pairs.stream().map(Pair::activeMask).distinct().count() != 15
                    || pairs.stream().anyMatch(p -> p.firstWins().size() != width))
                throw new IllegalArgumentException(
                        "Expected six hands, fifteen pairs and matching vectors");
        }

        public Pair pair(int mask) {
            return pairs.stream().filter(p -> p.activeMask() == mask).findFirst().orElseThrow();
        }
    }

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String classifier,
            String sourcePackHash,
            String sourceSpotHash,
            List<Signal> signals,
            List<Deal> deals) {
        public Artifact {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !CLASSIFIER.equals(classifier))
                throw new IllegalArgumentException("Unsupported rank/texture table identity");
            for (String hash : List.of(sourcePackHash, sourceSpotHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid source hash");
            signals = List.copyOf(signals);
            deals = List.copyOf(deals);
            if (signals.isEmpty()
                    || signals.size() > MAX_SIGNALS
                    || !signals.equals(signals.stream().distinct().sorted().toList())
                    || deals.isEmpty()
                    || deals.size() > 12)
                throw new IllegalArgumentException(
                        "Invalid bounded rank/texture palette or private support");
            int width = signals.size();
            if (deals.stream().anyMatch(d -> d.flopCounts().size() != width))
                throw new IllegalArgumentException("Palette and deal vector widths differ");
        }
    }

    private SixMaxRankTexturePayoffTable() {}

    public static Artifact generate(SixMaxPreflopSolutionPack source, IntConsumer progress) {
        Objects.requireNonNull(progress, "progress");
        if (!MultiwaySolutionPack.EXACT_ENUMERATION.equals(source.payoffMethod()))
            throw new IllegalArgumentException("Exact source payoffs required");
        var game = source.rebuildGame();
        var roots = game.chanceOutcomes(game.initialState());
        if (roots.size() > 12) throw new IllegalArgumentException("At most twelve private worlds");
        var palette = new TreeSet<Signal>();
        var allHands = roots.stream().map(r -> game.dealtHands(r.state())).toList();
        for (var hands : allHands) palette.addAll(physicalCounts(hands).keySet());
        var signals = List.copyOf(palette);
        var deals = new ArrayList<Deal>();
        for (var hands : allHands) {
            deals.add(enumerate(hands, SixMaxConditionalPayoffEnumeration.undealt(hands), signals));
            progress.accept(deals.size());
        }
        var artifact =
                new Artifact(
                        SCHEMA,
                        "VALIDATION_ONLY",
                        CLASSIFIER,
                        MultiwayPackJson.fullRoundContentHash(source),
                        source.spotHash(),
                        signals,
                        deals);
        validate(artifact, source);
        return artifact;
    }

    // Small decks are for independent tests only; production always uses all forty undealt cards.
    static Deal enumerate(List<WeightedCombo> hands, List<Card> deck, List<Signal> signals) {
        var indices = new java.util.HashMap<Signal, Integer>();
        for (int index = 0; index < signals.size(); index++)
            if (indices.put(signals.get(index), index) != null)
                throw new IllegalArgumentException("Duplicate signal");
        var exact =
                SixMaxConditionalPayoffEnumeration.enumerate(
                        hands,
                        deck,
                        signals.size(),
                        (a, b, c) -> {
                            var index = indices.get(Signal.from(a, b, c));
                            if (index == null)
                                throw new IllegalArgumentException(
                                        "Palette omits a legal flop signal");
                            return index;
                        });
        return new Deal(
                hands.stream().map(WeightedCombo::key).toList(),
                exact.flopCounts(),
                exact.pairs().stream()
                        .map(p -> new Pair(p.activeMask(), p.firstWins(), p.ties()))
                        .toList());
    }

    static Map<Signal, Long> physicalCounts(List<WeightedCombo> hands) {
        return physicalCountsForDeck(SixMaxConditionalPayoffEnumeration.undealt(hands));
    }

    static Map<Signal, Long> physicalCountsForDeck(List<Card> deck) {
        var counts = new TreeMap<Signal, Long>();
        for (int a = 0; a < deck.size() - 2; a++)
            for (int b = a + 1; b < deck.size() - 1; b++)
                for (int c = b + 1; c < deck.size(); c++)
                    counts.merge(Signal.from(deck.get(a), deck.get(b), deck.get(c)), 1L, Long::sum);
        return counts;
    }

    public static void validate(Artifact artifact, SixMaxPreflopSolutionPack source) {
        if (!artifact.sourcePackHash().equals(MultiwayPackJson.fullRoundContentHash(source))
                || !artifact.sourceSpotHash().equals(source.spotHash())
                || !MultiwaySolutionPack.EXACT_ENUMERATION.equals(source.payoffMethod()))
            throw new IllegalArgumentException("Table requires the exact declared source");
        var game = source.rebuildGame();
        var roots = game.chanceOutcomes(game.initialState());
        if (roots.size() != artifact.deals().size())
            throw new IllegalArgumentException("Private support differs");
        var palette = new TreeSet<Signal>();
        for (int index = 0; index < roots.size(); index++) {
            var hands = game.dealtHands(roots.get(index).state());
            var deal = artifact.deals().get(index);
            if (!deal.hands().equals(hands.stream().map(WeightedCombo::key).toList()))
                throw new IllegalArgumentException("Private support/order differs");
            var physical = physicalCounts(hands);
            palette.addAll(physical.keySet());
            for (int signal = 0; signal < artifact.signals().size(); signal++)
                if (!deal.flopCounts()
                        .get(signal)
                        .equals(physical.getOrDefault(artifact.signals().get(signal), 0L)))
                    throw new IllegalArgumentException(
                            "Rank/texture counts disagree with physical blockers");
            for (var pair : deal.pairs()) {
                long wins = 0, ties = 0;
                for (int signal = 0; signal < artifact.signals().size(); signal++) {
                    long runouts = deal.flopCounts().get(signal) * RUNOUTS_PER_FLOP;
                    long w = pair.firstWins().get(signal), t = pair.ties().get(signal);
                    if (w > runouts || t > runouts - w)
                        throw new IllegalArgumentException("Pair counts exceed physical runouts");
                    wins += w;
                    ties += t;
                }
                var original =
                        source.payoffs().stream()
                                .filter(
                                        p ->
                                                p.activeMask() == pair.activeMask()
                                                        && p.dealtCombos().equals(deal.hands()))
                                .findFirst()
                                .orElseThrow();
                double marginal = (wins + .5 * ties) / 6580080;
                if (Math.abs(
                                marginal
                                        - original.estimate()
                                                .shares()[
                                                Integer.numberOfTrailingZeros(pair.activeMask())])
                        > 1e-12)
                    throw new IllegalArgumentException(
                            "Rank/texture payoffs do not recover exact source marginal");
            }
        }
        if (!artifact.signals().equals(List.copyOf(palette)))
            throw new IllegalArgumentException("Palette is not complete physical support");
    }

    private static List<Long> counts(List<Long> counts) {
        var values = List.copyOf(counts);
        if (values.isEmpty() || values.size() > MAX_SIGNALS || values.stream().anyMatch(v -> v < 0))
            throw new IllegalArgumentException("Invalid bounded count vector");
        return values;
    }

    public static String json(Object value) throws Exception {
        return SixMaxTextureStudy.json(value);
    }

    public static String hash(Artifact artifact) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        SixMaxTexturePayoffTable.mapper()
                                                .writeValueAsBytes(artifact)));
    }

    public static Artifact read(Path input, SixMaxPreflopSolutionPack source) throws Exception {
        var result =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(readBytes(input, 4 * 1024 * 1024), Artifact.class);
        validate(result, source);
        return result;
    }

    static byte[] readBytes(Path input, int limit) throws Exception {
        if (Files.size(input) > limit)
            throw new IllegalArgumentException("Artifact input cap exceeded");
        if (!input.toString().endsWith(".gz")) return Files.readAllBytes(input);
        byte[] bytes;
        try (var zip = new java.util.zip.GZIPInputStream(Files.newInputStream(input))) {
            bytes = zip.readNBytes(limit + 1);
        }
        if (bytes.length > limit)
            throw new IllegalArgumentException("Expanded artifact cap exceeded");
        return bytes;
    }

    static void writeBytes(Path output, byte[] bytes, int limit) throws Exception {
        if (bytes.length > limit)
            throw new IllegalArgumentException("Artifact output cap exceeded");
        if (output.toString().endsWith(".gz")) {
            var compressed = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(compressed)) {
                zip.write(bytes);
            }
            bytes = compressed.toByteArray();
            if (bytes.length > limit)
                throw new IllegalArgumentException("Compressed artifact output cap exceeded");
        }
        SixMaxTexturePayoffTableMain.atomicWrite(output.toAbsolutePath().normalize(), bytes);
    }
}
