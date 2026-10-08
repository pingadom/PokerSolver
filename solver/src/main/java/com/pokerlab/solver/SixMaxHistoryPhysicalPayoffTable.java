package com.pokerlab.solver;

import com.pokerlab.core.card.Card;
import com.pokerlab.solver.SixMaxRankTextureFlopGame.Selection;
import com.pokerlab.solver.SixMaxSuitRefinementPayoffTable.Observation;
import com.pokerlab.solver.SixMaxSuitRefinementPayoffTable.Pair;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.function.IntConsumer;

/** Fixed public history/card whitelist with exact, history-specific rank/texture complements. */
public final class SixMaxHistoryPhysicalPayoffTable {
    public static final String SCHEMA = "six-max-history-physical-payoffs/v1";
    public static final String MODEL =
            "PUBLIC_HISTORY_FLOP_WHITELIST_OTHERWISE_RANK_TEXTURE_ONE_BET/v1";
    public static final int MAX_REVELATIONS = 600;
    public static final int MAX_OBSERVATIONS = 2000;
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final int MAX_MENU_BYTES = 128 * 1024;

    public record Reveal(int history, List<String> board) implements Comparable<Reveal> {
        public Reveal {
            board = List.copyOf(board);
            if (history < 0 || history >= 6 || board.size() != 3)
                throw new IllegalArgumentException("Reveal requires a history and three cards");
            var cards = board.stream().map(Card::parse).toList();
            new Observation(
                    SixMaxRankTexturePayoffTable.Signal.from(
                            cards.get(0), cards.get(1), cards.get(2)),
                    board);
        }

        Observation observation() {
            var cards = board.stream().map(Card::parse).toList();
            return new Observation(
                    SixMaxRankTexturePayoffTable.Signal.from(
                            cards.get(0), cards.get(1), cards.get(2)),
                    board);
        }

        @Override
        public int compareTo(Reveal other) {
            int h = Integer.compare(history, other.history);
            return h != 0 ? h : String.join("", board).compareTo(String.join("", other.board));
        }
    }

    public record Menu(List<Selection> selections, List<Reveal> revelations) {
        public Menu {
            selections = List.copyOf(selections);
            revelations = List.copyOf(revelations);
            int historyCount = selections.size();
            if (selections.isEmpty()
                    || selections.size() > 6
                    || selections.stream().map(Selection::history).distinct().count()
                            != selections.size()
                    || revelations.isEmpty()
                    || revelations.size() > MAX_REVELATIONS
                    || !revelations.equals(revelations.stream().distinct().sorted().toList())
                    || revelations.stream().anyMatch(r -> r.history() >= historyCount))
                throw new IllegalArgumentException("Invalid bounded public history/board menu");
        }
    }

    public record Deal(List<String> hands, List<Long> flopCounts, Pair activePair) {
        public Deal {
            hands = List.copyOf(hands);
            flopCounts = List.copyOf(flopCounts);
            Objects.requireNonNull(activePair, "activePair");
            if (hands.size() != 6
                    || flopCounts.isEmpty()
                    || flopCounts.size() > MAX_OBSERVATIONS
                    || flopCounts.stream().anyMatch(n -> n < 0)
                    || flopCounts.stream().mapToLong(Long::longValue).sum() != 9880
                    || activePair.firstWins().size() != flopCounts.size())
                throw new IllegalArgumentException("Invalid history-specific private deal");
        }
    }

    public record History(String publicHistory, int activeMask, List<Deal> deals) {
        public History {
            deals = List.copyOf(deals);
            if (publicHistory == null
                    || publicHistory.isEmpty()
                    || (activeMask & ~63) != 0
                    || Integer.bitCount(activeMask) != 2
                    || deals.isEmpty()
                    || deals.size() > 12
                    || deals.stream().anyMatch(d -> d.activePair().activeMask() != activeMask))
                throw new IllegalArgumentException("Invalid heads-up history payoffs");
        }
    }

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            String model,
            String sourcePackHash,
            String sourceSpotHash,
            String parentRankTableHash,
            Menu menu,
            List<Observation> observations,
            List<History> histories,
            long completeTreeStates) {
        public Artifact {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !MODEL.equals(model))
                throw new IllegalArgumentException("Unsupported history-specific table identity");
            for (String hash : List.of(sourcePackHash, sourceSpotHash, parentRankTableHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid history table hash");
            Objects.requireNonNull(menu, "menu");
            observations = List.copyOf(observations);
            histories = List.copyOf(histories);
            int width = observations.size();
            if (observations.isEmpty()
                    || observations.size() > MAX_OBSERVATIONS
                    || !observations.equals(observations.stream().distinct().sorted().toList())
                    || histories.size() != menu.selections().size()
                    || histories.stream().map(History::publicHistory).distinct().count()
                            != histories.size()
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxOneBetFlopGame.MAX_COMPLETE_STATES)
                throw new IllegalArgumentException("Invalid history table sizing or palette");
            for (var history : histories)
                if (history.deals().stream().anyMatch(d -> d.flopCounts().size() != width))
                    throw new IllegalArgumentException("History observation widths differ");
        }
    }

    /** Only exact generation/replay can produce a table accepted by the game and writer. */
    public static final class Verified {
        private final Artifact artifact;

        private Verified(Artifact artifact) {
            this.artifact = artifact;
        }

        public Artifact artifact() {
            return artifact;
        }
    }

    @FunctionalInterface
    interface PairOracle {
        SixMaxConditionalPayoffEnumeration.Pair evaluate(
                List<WeightedCombo> hands, List<Card> board, List<Card> remaining, int mask);
    }

    private record Prepared(
            List<Observation> observations,
            List<List<WeightedCombo>> hands,
            long[][][] counts,
            List<SixMaxRankTextureFlopGame.Coverage> coverage,
            long states) {}

    private SixMaxHistoryPhysicalPayoffTable() {}

    public record Sizing(
            int histories, int privateWorlds, int observations, long completeTreeStates) {
        public Sizing {
            if (histories < 1
                    || histories > 6
                    || privateWorlds < 1
                    || privateWorlds > 12
                    || observations < 1
                    || observations > MAX_OBSERVATIONS
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxOneBetFlopGame.MAX_COMPLETE_STATES)
                throw new IllegalArgumentException("Invalid bounded history model sizing");
        }
    }

    /** Validate the fixed menu and all counterfactual support before any expensive payoff work. */
    public static Sizing sizing(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            Menu menu)
            throws Exception {
        var prepared = prepare(source, parent, menu);
        return new Sizing(
                menu.selections().size(),
                prepared.hands().size(),
                prepared.observations().size(),
                prepared.states());
    }

    public static Verified generate(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            Menu menu,
            IntConsumer progress)
            throws Exception {
        return generate(
                source, parent, menu, progress, SixMaxConditionalPayoffEnumeration::fixedFlopPair);
    }

    // Synthetic accounting tests inject an all-tie oracle; production always uses exact cards.
    static Verified generate(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            Menu menu,
            IntConsumer progress,
            PairOracle oracle)
            throws Exception {
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(oracle, "oracle");
        var prepared = prepare(source, parent, menu);
        var indices = new HashMap<Observation, Integer>();
        for (int o = 0; o < prepared.observations().size(); o++)
            indices.put(prepared.observations().get(o), o);
        var histories = new ArrayList<History>();
        var cache = new HashMap<String, SixMaxConditionalPayoffEnumeration.Pair>();
        int completed = 0;
        for (int h = 0; h < menu.selections().size(); h++) {
            var coverage = prepared.coverage().get(h);
            int mask =
                    (1 << coverage.firstToAct().ordinal())
                            | (1 << coverage.secondToAct().ordinal());
            var deals = new ArrayList<Deal>();
            for (int d = 0; d < prepared.hands().size(); d++) {
                var hands = prepared.hands().get(d);
                var deck = SixMaxConditionalPayoffEnumeration.undealt(hands);
                var old = parent.deals().get(d);
                var oldPair = old.pair(mask);
                long[] wins = new long[prepared.observations().size()],
                        ties = new long[wins.length];
                for (int r = 0; r < parent.signals().size(); r++) {
                    int o = indices.get(new Observation(parent.signals().get(r), List.of()));
                    wins[o] = oldPair.firstWins().get(r);
                    ties[o] = oldPair.ties().get(r);
                }
                for (var reveal : menu.revelations()) {
                    if (reveal.history() != h) continue;
                    var observation = reveal.observation();
                    int o = indices.get(observation);
                    if (prepared.counts()[h][d][o] == 0) continue;
                    String key = d + ":" + mask + ":" + observation.key();
                    var exact = cache.get(key);
                    if (exact == null) {
                        var board = reveal.board().stream().map(Card::parse).toList();
                        var remaining = deck.stream().filter(c -> !board.contains(c)).toList();
                        if (remaining.size() != 37)
                            throw new IllegalStateException("Literal board needs 666 runouts");
                        exact = oracle.evaluate(hands, board, remaining, mask);
                        cache.put(key, exact);
                    }
                    if (exact.activeMask() != mask
                            || exact.firstWins().size() != 1
                            || exact.ties().size() != 1)
                        throw new IllegalStateException("Exact active pair identity differs");
                    wins[o] = exact.firstWins().getFirst();
                    ties[o] = exact.ties().getFirst();
                    int rank = indices.get(new Observation(observation.signal(), List.of()));
                    wins[rank] -= wins[o];
                    ties[rank] -= ties[o];
                }
                deals.add(
                        new Deal(
                                old.hands(),
                                longs(prepared.counts()[h][d]),
                                new Pair(mask, longs(wins), longs(ties))));
                progress.accept(++completed);
            }
            histories.add(new History(coverage.history(), mask, deals));
        }
        var artifact =
                new Artifact(
                        SCHEMA,
                        "VALIDATION_ONLY",
                        MODEL,
                        parent.sourcePackHash(),
                        parent.sourceSpotHash(),
                        SixMaxRankTexturePayoffTable.hash(parent),
                        menu,
                        prepared.observations(),
                        histories,
                        prepared.states());
        validate(artifact, source, parent);
        return new Verified(artifact);
    }

    private static Prepared prepare(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            Menu menu)
            throws Exception {
        SixMaxRankTexturePayoffTable.validate(parent, source);
        var base =
                new SixMaxOneBetFlopGame(
                        source, SixMaxFlopPayoffView.rank(parent), menu.selections());
        var hands =
                base.sourceGame().chanceOutcomes(base.sourceGame().initialState()).stream()
                        .map(r -> base.sourceGame().dealtHands(r.state()))
                        .toList();
        var palette = new TreeSet<Observation>();
        parent.signals().forEach(s -> palette.add(new Observation(s, List.of())));
        for (var reveal : menu.revelations()) {
            var observation = reveal.observation();
            if (!parent.signals().contains(observation.signal()))
                throw new IllegalArgumentException("Unsupported revealed rank signal");
            var board = reveal.board().stream().map(Card::parse).toList();
            if (hands.stream()
                    .noneMatch(
                            world ->
                                    SixMaxConditionalPayoffEnumeration.undealt(world)
                                            .containsAll(board)))
                throw new IllegalArgumentException(
                        "Revealed flop is blocked in every private world");
            palette.add(observation);
        }
        if (palette.size() > MAX_OBSERVATIONS)
            throw new IllegalArgumentException("History observation cap exceeded");
        var observations = List.copyOf(palette);
        var indices = new HashMap<Observation, Integer>();
        for (int o = 0; o < observations.size(); o++) indices.put(observations.get(o), o);
        long[][][] counts = new long[menu.selections().size()][hands.size()][observations.size()];
        for (int h = 0; h < menu.selections().size(); h++)
            for (int d = 0; d < hands.size(); d++) {
                for (int r = 0; r < parent.signals().size(); r++)
                    counts[h][d][indices.get(new Observation(parent.signals().get(r), List.of()))] =
                            parent.deals().get(d).flopCounts().get(r);
                var deck = SixMaxConditionalPayoffEnumeration.undealt(hands.get(d));
                for (var reveal : menu.revelations()) {
                    if (reveal.history() != h
                            || !deck.containsAll(reveal.board().stream().map(Card::parse).toList()))
                        continue;
                    var o = reveal.observation();
                    counts[h][d][indices.get(o)] = 1;
                    if (--counts[h][d][indices.get(new Observation(o.signal(), List.of()))] < 0)
                        throw new IllegalArgumentException("Reveals exceed physical rank counts");
                }
            }
        var coverage = new ArrayList<SixMaxRankTextureFlopGame.Coverage>();
        for (var selection : menu.selections())
            coverage.add(
                    base.coverage().stream()
                            .filter(c -> c.history().equals(base.historyKey(selection.history())))
                            .findFirst()
                            .orElseThrow());
        long states = 1L + (long) base.sourceGame().treeSummary().totalStates() * hands.size();
        for (var history : counts)
            for (var world : history)
                states += 9L * Arrays.stream(world).filter(n -> n > 0).count();
        if (states > SixMaxOneBetFlopGame.MAX_COMPLETE_STATES)
            throw new IllegalArgumentException("History physical complete-state cap exceeded");
        return new Prepared(observations, hands, counts, List.copyOf(coverage), states);
    }

    /**
     * Structural/count/complement validation; exact generation/replay additionally checks each
     * literal payoff.
     */
    static void validate(
            Artifact artifact,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent)
            throws Exception {
        if (!artifact.sourcePackHash().equals(parent.sourcePackHash())
                || !artifact.sourceSpotHash().equals(parent.sourceSpotHash())
                || !artifact.parentRankTableHash()
                        .equals(SixMaxRankTexturePayoffTable.hash(parent)))
            throw new IllegalArgumentException("History payoff source/parent differs");
        var prepared = prepare(source, parent, artifact.menu());
        if (!prepared.observations().equals(artifact.observations())
                || prepared.states() != artifact.completeTreeStates())
            throw new IllegalArgumentException("History payoff palette or complete sizing differs");
        for (int h = 0; h < artifact.histories().size(); h++) {
            var history = artifact.histories().get(h);
            var coverage = prepared.coverage().get(h);
            int mask =
                    (1 << coverage.firstToAct().ordinal())
                            | (1 << coverage.secondToAct().ordinal());
            if (!history.publicHistory().equals(coverage.history())
                    || history.activeMask() != mask
                    || history.deals().size() != parent.deals().size())
                throw new IllegalArgumentException("History identity or private support differs");
            for (int d = 0; d < history.deals().size(); d++) {
                var row = history.deals().get(d);
                var old = parent.deals().get(d);
                if (!row.hands().equals(old.hands())
                        || !row.flopCounts().equals(longs(prepared.counts()[h][d])))
                    throw new IllegalArgumentException(
                            "History physical counts/private blockers differ");
                long[] counts = new long[parent.signals().size()],
                        wins = new long[counts.length],
                        ties = new long[counts.length];
                for (int o = 0; o < artifact.observations().size(); o++) {
                    int rank = parent.signals().indexOf(artifact.observations().get(o).signal());
                    long count = row.flopCounts().get(o),
                            w = row.activePair().firstWins().get(o),
                            t = row.activePair().ties().get(o);
                    if (w > count * 666 || t > count * 666 - w)
                        throw new IllegalArgumentException("History payoff exceeds runouts");
                    counts[rank] += count;
                    wins[rank] += w;
                    ties[rank] += t;
                }
                if (!longs(counts).equals(old.flopCounts())
                        || !longs(wins).equals(old.pair(mask).firstWins())
                        || !longs(ties).equals(old.pair(mask).ties()))
                    throw new IllegalArgumentException(
                            "History exact complement regrouping differs");
            }
        }
    }

    static SixMaxFlopPayoffView view(Verified verified) throws Exception {
        var table = verified.artifact();
        String namespace = "history-physical:" + hash(table);
        var histories = new HashMap<String, History>();
        table.histories().forEach(h -> histories.put(h.publicHistory(), h));
        return new SixMaxFlopPayoffView() {
            public String namespace() {
                return namespace;
            }

            public int dealCount() {
                return table.histories().getFirst().deals().size();
            }

            public List<String> hands(int d) {
                return table.histories().getFirst().deals().get(d).hands();
            }

            public List<Long> counts(int d) {
                throw new IllegalArgumentException(
                        "Public history required for observation counts");
            }

            public double share(int d, int mask, int player, int o) {
                throw new IllegalArgumentException("Public history required for payoffs");
            }

            public String key(int o) {
                return table.observations().get(o).key();
            }

            private History history(String key) {
                var history = histories.get(key);
                if (history == null) throw new IllegalArgumentException("Unknown payoff history");
                return history;
            }

            public List<Long> counts(String history, int d) {
                return history(history).deals().get(d).flopCounts();
            }

            public double share(String history, int d, int mask, int player, int o) {
                var h = history(history);
                if (h.activeMask() != mask)
                    throw new IllegalArgumentException("Wrong history active pair");
                var row = h.deals().get(d);
                return row.activePair().share(player, o, row.flopCounts().get(o) * 666);
            }
        };
    }

    public static String hash(Artifact artifact) throws Exception {
        return hashValue(artifact);
    }

    /** Public classification never receives a private world or a player's actual hand. */
    public static Observation observe(Menu menu, int history, List<Card> board) {
        if (history < 0
                || history >= menu.selections().size()
                || board.size() != 3
                || new HashSet<>(board).size() != 3)
            throw new IllegalArgumentException(
                    "A declared history and three distinct public cards are required");
        var cards = board.stream().map(Card::compact).sorted().toList();
        var reveal = new Reveal(history, cards);
        return new Observation(
                reveal.observation().signal(),
                menu.revelations().contains(reveal) ? cards : List.of());
    }

    static String hashValue(Object value) throws Exception {
        return java.util.HexFormat.of()
                .formatHex(
                        java.security.MessageDigest.getInstance("SHA-256")
                                .digest(
                                        SixMaxTexturePayoffTable.mapper()
                                                .writeValueAsBytes(value)));
    }

    private static List<Long> longs(long[] values) {
        return Arrays.stream(values).boxed().toList();
    }

    public static Menu readMenu(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        SixMaxRankTexturePayoffTable.readBytes(path, MAX_MENU_BYTES), Menu.class);
    }

    public static void writeMenu(Path path, Menu menu) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(menu).getBytes(StandardCharsets.UTF_8),
                MAX_MENU_BYTES);
    }

    public static void write(Path path, Verified verified) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(verified.artifact()).getBytes(StandardCharsets.UTF_8),
                MAX_BYTES);
    }

    public static Verified replay(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES),
                                Artifact.class);
        validate(saved, source, parent);
        var expected = generate(source, parent, saved.menu(), ignored -> {});
        if (!saved.equals(expected.artifact()))
            throw new IllegalArgumentException("Exact history physical payoff replay differs");
        return expected;
    }
}
