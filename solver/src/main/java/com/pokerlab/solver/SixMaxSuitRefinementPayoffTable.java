package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.SixMaxRankTexturePayoffTable.Signal;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.IntConsumer;

/** Partial public suit refinement, separately bound to an exact rank/texture parent table. */
public final class SixMaxSuitRefinementPayoffTable {
    public static final String SCHEMA = "six-max-suit-refinement-payoffs/v1";
    public static final String CLASSIFIER = "DECLARED_PHYSICAL_FLOPS_OTHERWISE_RANK_TEXTURE/v1";
    public static final int MAX_OBSERVATIONS = 2000;
    public static final int MAX_REFINED_SIGNALS = 16;
    static final int MAX_BYTES = 8 * 1024 * 1024;

    public record Observation(Signal signal, List<String> board)
            implements Comparable<Observation> {
        public Observation {
            Objects.requireNonNull(signal, "signal");
            board = List.copyOf(board);
            if (!board.isEmpty()) {
                if (board.size() != 3 || !board.equals(board.stream().distinct().sorted().toList()))
                    throw new IllegalArgumentException(
                            "Physical observations require three canonical distinct cards");
                var cards = board.stream().map(Card::parse).toList();
                if (!signal.equals(Signal.from(cards.get(0), cards.get(1), cards.get(2))))
                    throw new IllegalArgumentException("Physical board and rank/texture disagree");
                if (!board.equals(cards.stream().map(Card::compact).toList()))
                    throw new IllegalArgumentException("Noncanonical card notation");
            }
        }

        public String key() {
            return board.isEmpty() ? "rank:" + signal.key() : "board:" + String.join("", board);
        }

        public boolean physical() {
            return !board.isEmpty();
        }

        @Override
        public int compareTo(Observation other) {
            int rank = signal.compareTo(other.signal);
            return rank != 0 ? rank : key().compareTo(other.key());
        }
    }

    public record Pair(int activeMask, List<Long> firstWins, List<Long> ties) {
        public Pair {
            if ((activeMask & ~63) != 0 || Integer.bitCount(activeMask) != 2)
                throw new IllegalArgumentException("Pair requires two of six seats");
            firstWins = counts(firstWins);
            ties = counts(ties);
            if (firstWins.size() != ties.size())
                throw new IllegalArgumentException("Pair widths differ");
        }

        public double share(int player, int observation, long runouts) {
            if (player < 0
                    || player >= 6
                    || (activeMask & (1 << player)) == 0
                    || observation < 0
                    || observation >= firstWins.size()
                    || runouts <= 0
                    || firstWins.get(observation) > runouts
                    || ties.get(observation) > runouts - firstWins.get(observation))
                throw new IllegalArgumentException("Invalid player, observation or runout counts");
            double first = (firstWins.get(observation) + .5 * ties.get(observation)) / runouts;
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
                        "Expected six hands and fifteen matching pair vectors");
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
            String parentRankTableHash,
            List<Signal> refinedSignals,
            List<Observation> observations,
            List<Deal> deals) {
        public Artifact {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !CLASSIFIER.equals(classifier))
                throw new IllegalArgumentException("Unsupported suit-refinement identity");
            for (String hash : List.of(sourcePackHash, sourceSpotHash, parentRankTableHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid suit-refinement hash");
            refinedSignals = refinement(refinedSignals);
            observations = List.copyOf(observations);
            deals = List.copyOf(deals);
            if (observations.isEmpty()
                    || observations.size() > MAX_OBSERVATIONS
                    || !observations.equals(observations.stream().distinct().sorted().toList())
                    || deals.isEmpty()
                    || deals.size() > 12)
                throw new IllegalArgumentException(
                        "Invalid bounded observations or private worlds");
            for (var observation : observations)
                if (observation.physical() != refinedSignals.contains(observation.signal()))
                    throw new IllegalArgumentException(
                            "Observation disagrees with declared public refinement");
            int width = observations.size();
            if (deals.stream().anyMatch(d -> d.flopCounts().size() != width))
                throw new IllegalArgumentException("Observation and deal widths differ");
        }
    }

    private SixMaxSuitRefinementPayoffTable() {}

    static List<Signal> refinement(List<Signal> signals) {
        var result = List.copyOf(signals);
        if (result.isEmpty()
                || result.size() > MAX_REFINED_SIGNALS
                || !result.equals(result.stream().distinct().sorted().toList()))
            throw new IllegalArgumentException(
                    "Require one to sixteen canonical distinct refined signals");
        return result;
    }

    public static Observation observe(List<Card> board, List<Signal> refined) {
        if (board.size() != 3) throw new IllegalArgumentException("Three public cards required");
        var signal = Signal.from(board.get(0), board.get(1), board.get(2));
        return new Observation(
                signal,
                refined.contains(signal)
                        ? board.stream().map(Card::compact).sorted().toList()
                        : List.of());
    }

    static Map<Observation, Long> physicalCounts(List<WeightedCombo> hands, List<Signal> refined) {
        var deck = SixMaxConditionalPayoffEnumeration.undealt(hands);
        var counts = new TreeMap<Observation, Long>();
        for (int a = 0; a < deck.size() - 2; a++)
            for (int b = a + 1; b < deck.size() - 1; b++)
                for (int c = b + 1; c < deck.size(); c++)
                    counts.merge(
                            observe(List.of(deck.get(a), deck.get(b), deck.get(c)), refined),
                            1L,
                            Long::sum);
        return counts;
    }

    /**
     * Reuse untouched exact aggregates; enumerate all physical flops within every refined group.
     */
    public static Artifact generate(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            List<Signal> refined,
            IntConsumer progress)
            throws Exception {
        Objects.requireNonNull(progress, "progress");
        refined = refinement(refined);
        SixMaxRankTexturePayoffTable.validate(parent, source);
        if (!parent.signals().containsAll(refined))
            throw new IllegalArgumentException("Refined signals lack source support");
        var game = source.rebuildGame();
        var allHands =
                game.chanceOutcomes(game.initialState()).stream()
                        .map(r -> game.dealtHands(r.state()))
                        .toList();
        var worldCounts = new ArrayList<Map<Observation, Long>>();
        var palette = new TreeSet<Observation>();
        for (var hands : allHands) {
            var counts = physicalCounts(hands, refined);
            worldCounts.add(counts);
            palette.addAll(counts.keySet());
        }
        if (palette.size() > MAX_OBSERVATIONS)
            throw new IllegalArgumentException("Suit observation cap exceeded");
        var observations = List.copyOf(palette);
        var deals = new ArrayList<Deal>();
        for (int world = 0; world < allHands.size(); world++) {
            var hands = allHands.get(world);
            var old = parent.deals().get(world);
            var deck = SixMaxConditionalPayoffEnumeration.undealt(hands);
            long[][] wins = new long[15][observations.size()],
                    ties = new long[15][observations.size()];
            var counts = new ArrayList<Long>();
            for (int o = 0; o < observations.size(); o++) {
                var observation = observations.get(o);
                long n = worldCounts.get(world).getOrDefault(observation, 0L);
                counts.add(n);
                if (n == 0) continue;
                if (!observation.physical()) {
                    int rank = parent.signals().indexOf(observation.signal());
                    for (int p = 0; p < 15; p++) {
                        wins[p][o] = old.pairs().get(p).firstWins().get(rank);
                        ties[p][o] = old.pairs().get(p).ties().get(rank);
                    }
                } else {
                    var board = observation.board().stream().map(Card::parse).toList();
                    var remaining = deck.stream().filter(c -> !board.contains(c)).toList();
                    if (n != 1 || remaining.size() != 37)
                        throw new IllegalStateException("Physical flop must have 666 runouts");
                    var exact =
                            SixMaxConditionalPayoffEnumeration.fixedFlop(hands, board, remaining);
                    for (int p = 0; p < 15; p++) {
                        int mask = old.pairs().get(p).activeMask();
                        var pair =
                                exact.pairs().stream()
                                        .filter(q -> q.activeMask() == mask)
                                        .findFirst()
                                        .orElseThrow();
                        wins[p][o] = pair.firstWins().getFirst();
                        ties[p][o] = pair.ties().getFirst();
                    }
                }
            }
            var pairs = new ArrayList<Pair>();
            for (int p = 0; p < 15; p++)
                pairs.add(
                        new Pair(old.pairs().get(p).activeMask(), longs(wins[p]), longs(ties[p])));
            deals.add(new Deal(old.hands(), counts, pairs));
            progress.accept(deals.size());
        }
        var result =
                new Artifact(
                        SCHEMA,
                        "VALIDATION_ONLY",
                        CLASSIFIER,
                        parent.sourcePackHash(),
                        parent.sourceSpotHash(),
                        SixMaxRankTexturePayoffTable.hash(parent),
                        refined,
                        observations,
                        deals);
        validate(result, source, parent);
        return result;
    }

    public static void validate(
            Artifact table,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent)
            throws Exception {
        SixMaxRankTexturePayoffTable.validate(parent, source);
        if (!table.sourcePackHash().equals(parent.sourcePackHash())
                || !table.sourceSpotHash().equals(parent.sourceSpotHash())
                || !table.parentRankTableHash().equals(SixMaxRankTexturePayoffTable.hash(parent))
                || !parent.signals().containsAll(table.refinedSignals())
                || table.deals().size() != parent.deals().size())
            throw new IllegalArgumentException("Suit table source or parent identity differs");
        var game = source.rebuildGame();
        var roots = game.chanceOutcomes(game.initialState());
        var palette = new TreeSet<Observation>();
        for (int world = 0; world < roots.size(); world++) {
            var old = parent.deals().get(world);
            var row = table.deals().get(world);
            if (!row.hands().equals(old.hands()))
                throw new IllegalArgumentException("Suit private support order differs");
            var physical =
                    physicalCounts(
                            game.dealtHands(roots.get(world).state()), table.refinedSignals());
            palette.addAll(physical.keySet());
            long[] groupedCounts = new long[parent.signals().size()];
            long[][] wins = new long[15][parent.signals().size()],
                    ties = new long[15][parent.signals().size()];
            for (int o = 0; o < table.observations().size(); o++) {
                var observation = table.observations().get(o);
                long count = row.flopCounts().get(o);
                if (count != physical.getOrDefault(observation, 0L))
                    throw new IllegalArgumentException(
                            "Suit counts disagree with physical blockers");
                int rank = parent.signals().indexOf(observation.signal());
                if (rank < 0)
                    throw new IllegalArgumentException("Suit palette has unsupported rank signal");
                groupedCounts[rank] += count;
                for (int p = 0; p < 15; p++) {
                    var pair = row.pair(old.pairs().get(p).activeMask());
                    long w = pair.firstWins().get(o), t = pair.ties().get(o), runouts = count * 666;
                    if (w > runouts || t > runouts - w)
                        throw new IllegalArgumentException("Suit counts exceed physical runouts");
                    wins[p][rank] += w;
                    ties[p][rank] += t;
                }
            }
            if (!longs(groupedCounts).equals(old.flopCounts()))
                throw new IllegalArgumentException("Suit flop regrouping differs");
            for (int p = 0; p < 15; p++)
                if (!longs(wins[p]).equals(old.pairs().get(p).firstWins())
                        || !longs(ties[p]).equals(old.pairs().get(p).ties()))
                    throw new IllegalArgumentException(
                            "Suit payoff regrouping differs from exact parent");
        }
        if (!table.observations().equals(List.copyOf(palette)))
            throw new IllegalArgumentException("Suit palette omits or adds physical support");
    }

    static SixMaxFlopPayoffView view(Artifact table) {
        return new SixMaxFlopPayoffView() {
            public String namespace() {
                return "suit-refinement";
            }

            public int dealCount() {
                return table.deals().size();
            }

            public List<String> hands(int d) {
                return table.deals().get(d).hands();
            }

            public List<Long> counts(int d) {
                return table.deals().get(d).flopCounts();
            }

            public String key(int o) {
                return table.observations().get(o).key();
            }

            public double share(int d, int mask, int player, int o) {
                return table.deals().get(d).pair(mask).share(player, o, counts(d).get(o) * 666);
            }
        };
    }

    private static List<Long> counts(List<Long> values) {
        var copy = List.copyOf(values);
        if (copy.isEmpty() || copy.size() > MAX_OBSERVATIONS || copy.stream().anyMatch(n -> n < 0))
            throw new IllegalArgumentException("Invalid suit count vector");
        return copy;
    }

    private static List<Long> longs(long[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    public static String hash(Artifact table) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        SixMaxTexturePayoffTable.mapper()
                                                .writeValueAsBytes(table)));
    }

    public static Artifact read(
            Path input,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent)
            throws Exception {
        var table =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(input, MAX_BYTES),
                                Artifact.class);
        validate(table, source, parent);
        return table;
    }

    public static void write(
            Path output,
            Artifact table,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent)
            throws Exception {
        validate(table, source, parent);
        SixMaxRankTexturePayoffTable.writeBytes(
                output,
                SixMaxTextureStudy.json(table).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                MAX_BYTES);
    }
}
