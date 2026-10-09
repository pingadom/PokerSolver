package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuitRefinementPayoffTable.Observation;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** A lossless storage encoding of the existing game, not a broader observation model. */
public final class SixMaxHistoryPhysicalCompactStorage {
    public static final String SCHEMA = "six-max-history-physical-compact-storage/v1";
    public static final String ENCODING = "HISTORY_SUPPORT_DENSE_PRIMITIVE_LOOKUP/v1";
    public static final int MAX_BYTES = SixMaxHistoryPhysicalPayoffTable.MAX_BYTES;

    public record Deal(
            List<String> hands, List<Long> counts, List<Long> firstWins, List<Long> ties) {
        public Deal {
            hands = List.copyOf(hands);
            counts = List.copyOf(counts);
            firstWins = List.copyOf(firstWins);
            ties = List.copyOf(ties);
            if (hands.size() != 6
                    || counts.isEmpty()
                    || counts.size() > 2000
                    || firstWins.size() != counts.size()
                    || ties.size() != counts.size()
                    || counts.stream().anyMatch(n -> n < 0 || n > 9880)
                    || counts.stream().mapToLong(Long::longValue).sum() != 9880)
                throw new IllegalArgumentException("Invalid compact deal vectors");
            for (int i = 0; i < counts.size(); i++) {
                long runouts = counts.get(i) * 666, wins = firstWins.get(i), tie = ties.get(i);
                if (wins < 0 || wins > runouts || tie < 0 || tie > runouts - wins)
                    throw new IllegalArgumentException("Invalid compact exact payouts");
            }
        }
    }

    public record History(
            String publicHistory, int activeMask, List<Integer> globalIndices, List<Deal> deals) {
        public History {
            globalIndices = List.copyOf(globalIndices);
            deals = List.copyOf(deals);
            if (publicHistory == null
                    || publicHistory.isEmpty()
                    || (activeMask & ~63) != 0
                    || Integer.bitCount(activeMask) != 2
                    || globalIndices.isEmpty()
                    || globalIndices.size() > 2000
                    || deals.isEmpty()
                    || deals.size() > 12)
                throw new IllegalArgumentException("Invalid compact history");
            int previous = -1;
            for (int index : globalIndices) {
                if (index <= previous || index >= 2000)
                    throw new IllegalArgumentException("Compact indexes must increase");
                previous = index;
            }
            for (var deal : deals)
                if (deal.counts().size() != globalIndices.size())
                    throw new IllegalArgumentException("Compact support widths differ");
        }
    }

    public record Artifact(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            String encoding,
            String storedGameModel,
            String originalPayoffHash,
            String sourcePackHash,
            String sourceSpotHash,
            String parentRankTableHash,
            SixMaxHistoryPhysicalPayoffTable.Menu menu,
            List<Observation> observations,
            List<History> histories,
            long completeTreeStates) {
        public Artifact {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || trainerAdmission
                    || !ENCODING.equals(encoding)
                    || !SixMaxHistoryPhysicalPayoffTable.MODEL.equals(storedGameModel))
                throw new IllegalArgumentException("Unsupported compact storage identity");
            for (String hash :
                    List.of(
                            originalPayoffHash,
                            sourcePackHash,
                            sourceSpotHash,
                            parentRankTableHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid compact storage hash");
            Objects.requireNonNull(menu, "menu");
            observations = List.copyOf(observations);
            histories = List.copyOf(histories);
            if (observations.isEmpty()
                    || observations.size() > 2000
                    || !observations.equals(observations.stream().distinct().sorted().toList())
                    || histories.size() != menu.selections().size()
                    || histories.stream().map(History::publicHistory).distinct().count()
                            != histories.size()
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxOneBetFlopGame.MAX_COMPLETE_STATES)
                throw new IllegalArgumentException("Invalid compact sizing or palette");
            var first = histories.getFirst().deals().stream().map(Deal::hands).toList();
            for (var h : histories) {
                if (h.globalIndices().getLast() >= observations.size()
                        || !h.deals().stream().map(Deal::hands).toList().equals(first))
                    throw new IllegalArgumentException("Compact global/private support differs");
            }
        }
    }

    /** Payload counts, not an estimate of whole JVM heap or proof of improved performance. */
    public record Layout(
            int histories,
            int deals,
            int globalWidth,
            List<Integer> historyWidths,
            long rectangularEntries,
            long localEntries,
            long positiveEntries,
            long densePrimitiveVectorBytes,
            long lookupPrimitiveArrayBytes,
            int canonicalJsonBytes,
            int retainedEncodedBytes) {
        public Layout {
            historyWidths = List.copyOf(historyWidths);
        }
    }

    /**
     * Only projection from, or replay against, an exactly verified original table can create this.
     */
    public static final class Verified {
        private final byte[] encoded;
        private final String hash;
        private final SixMaxFlopPayoffView view;
        private final Layout layout;

        private Verified(Artifact artifact) throws Exception {
            byte[] canonical = SixMaxTexturePayoffTable.mapper().writeValueAsBytes(artifact);
            if (canonical.length > MAX_BYTES)
                throw new IllegalArgumentException("Compact byte cap exceeded");
            var compressed = new java.io.ByteArrayOutputStream();
            try (var gzip = new java.util.zip.GZIPOutputStream(compressed)) {
                gzip.write(canonical);
            }
            encoded = compressed.toByteArray();
            if (encoded.length > MAX_BYTES)
                throw new IllegalArgumentException("Compact encoded cache cap exceeded");
            hash = SixMaxHistoryPhysicalConditionalRefinement.hash(artifact);
            view = primitiveView(artifact);
            long rectangular =
                    (long) artifact.histories().size()
                            * artifact.histories().getFirst().deals().size()
                            * artifact.observations().size();
            long local = 0, positive = 0;
            for (var h : artifact.histories())
                for (var d : h.deals()) {
                    local += d.counts().size();
                    positive += d.counts().stream().filter(n -> n > 0).count();
                }
            layout =
                    new Layout(
                            artifact.histories().size(),
                            artifact.histories().getFirst().deals().size(),
                            artifact.observations().size(),
                            artifact.histories().stream()
                                    .map(h -> h.globalIndices().size())
                                    .toList(),
                            rectangular,
                            local,
                            positive,
                            rectangular * 3 * Long.BYTES,
                            local * 3 * Integer.BYTES
                                    + (long) artifact.histories().size()
                                            * artifact.observations().size()
                                            * Integer.BYTES,
                            canonical.length,
                            encoded.length);
        }

        public Artifact artifact() throws Exception {
            try (var gzip =
                    new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(encoded))) {
                var bytes = gzip.readNBytes(MAX_BYTES + 1);
                if (bytes.length > MAX_BYTES)
                    throw new IllegalStateException("Compact cache cap exceeded");
                return SixMaxTexturePayoffTable.mapper().readValue(bytes, Artifact.class);
            }
        }

        public String hash() {
            return hash;
        }

        public Layout layout() {
            return layout;
        }

        public MultiPlayerCfrGame<SixMaxRankTextureFlopGame.State> game(
                SixMaxPreflopSolutionPack source, SixMaxRankTexturePayoffTable.Artifact parent)
                throws Exception {
            return core(source, parent);
        }

        SixMaxOneBetFlopGame core(
                SixMaxPreflopSolutionPack source, SixMaxRankTexturePayoffTable.Artifact parent)
                throws Exception {
            var a = artifact();
            var restored = restore(a);
            SixMaxHistoryPhysicalPayoffTable.validate(restored, source, parent);
            if (!a.originalPayoffHash().equals(SixMaxHistoryPhysicalPayoffTable.hash(restored)))
                throw new IllegalStateException("Compact original table hash differs");
            var game = new SixMaxOneBetFlopGame(source, view, a.menu().selections());
            if (game.completeTreeStates() != a.completeTreeStates())
                throw new IllegalStateException("Compact complete states differ");
            return game;
        }
    }

    private record Row(int[] counts, int[] wins, int[] ties, List<Long> globalCounts) {}

    private record Lookup(int mask, int[] globalToLocal, List<Row> rows) {}

    private SixMaxHistoryPhysicalCompactStorage() {}

    public static Verified project(SixMaxHistoryPhysicalPayoffTable.Verified verified)
            throws Exception {
        var table = Objects.requireNonNull(verified, "verified").artifact();
        int width = table.observations().size();
        var histories = new ArrayList<History>();
        for (var h : table.histories()) {
            var indices = new ArrayList<Integer>();
            for (int o = 0; o < width; o++) {
                final int index = o;
                if (h.deals().stream().anyMatch(d -> d.flopCounts().get(index) > 0))
                    indices.add(index);
            }
            var deals = new ArrayList<Deal>();
            for (var d : h.deals())
                deals.add(
                        new Deal(
                                d.hands(),
                                indices.stream().map(d.flopCounts()::get).toList(),
                                indices.stream().map(d.activePair().firstWins()::get).toList(),
                                indices.stream().map(d.activePair().ties()::get).toList()));
            histories.add(new History(h.publicHistory(), h.activeMask(), indices, deals));
        }
        var artifact =
                new Artifact(
                        SCHEMA,
                        "VALIDATION_ONLY",
                        false,
                        ENCODING,
                        table.model(),
                        SixMaxHistoryPhysicalPayoffTable.hash(table),
                        table.sourcePackHash(),
                        table.sourceSpotHash(),
                        table.parentRankTableHash(),
                        table.menu(),
                        table.observations(),
                        histories,
                        table.completeTreeStates());
        if (!restore(artifact).equals(table))
            throw new IllegalStateException("Compact projection lost exact values");
        return new Verified(artifact);
    }

    static SixMaxHistoryPhysicalPayoffTable.Artifact restore(Artifact compact) {
        int width = compact.observations().size();
        var histories = new ArrayList<SixMaxHistoryPhysicalPayoffTable.History>();
        for (var h : compact.histories()) {
            var deals = new ArrayList<SixMaxHistoryPhysicalPayoffTable.Deal>();
            for (var d : h.deals()) {
                var counts = new ArrayList<>(Collections.nCopies(width, 0L));
                var wins = new ArrayList<>(counts);
                var ties = new ArrayList<>(counts);
                for (int o = 0; o < h.globalIndices().size(); o++) {
                    int global = h.globalIndices().get(o);
                    counts.set(global, d.counts().get(o));
                    wins.set(global, d.firstWins().get(o));
                    ties.set(global, d.ties().get(o));
                }
                deals.add(
                        new SixMaxHistoryPhysicalPayoffTable.Deal(
                                d.hands(),
                                counts,
                                new SixMaxSuitRefinementPayoffTable.Pair(
                                        h.activeMask(), wins, ties)));
            }
            histories.add(
                    new SixMaxHistoryPhysicalPayoffTable.History(
                            h.publicHistory(), h.activeMask(), deals));
        }
        return new SixMaxHistoryPhysicalPayoffTable.Artifact(
                SixMaxHistoryPhysicalPayoffTable.SCHEMA,
                "VALIDATION_ONLY",
                compact.storedGameModel(),
                compact.sourcePackHash(),
                compact.sourceSpotHash(),
                compact.parentRankTableHash(),
                compact.menu(),
                compact.observations(),
                histories,
                compact.completeTreeStates());
    }

    private static SixMaxFlopPayoffView primitiveView(Artifact artifact) {
        int width = artifact.observations().size();
        String namespace = "history-physical:" + artifact.originalPayoffHash();
        var keys = artifact.observations().stream().map(Observation::key).toList();
        var hands = artifact.histories().getFirst().deals().stream().map(Deal::hands).toList();
        var lookups = new HashMap<String, Lookup>();
        for (var h : artifact.histories()) {
            int[] mapping = new int[width];
            Arrays.fill(mapping, -1);
            for (int o = 0; o < h.globalIndices().size(); o++)
                mapping[h.globalIndices().get(o)] = o;
            var rows = new ArrayList<Row>();
            for (var d : h.deals()) {
                // Counts <= 9880 and payouts <= 9880*666; conversion is checked, never truncated.
                int[] counts = d.counts().stream().mapToInt(Math::toIntExact).toArray();
                int[] wins = d.firstWins().stream().mapToInt(Math::toIntExact).toArray();
                int[] ties = d.ties().stream().mapToInt(Math::toIntExact).toArray();
                List<Long> global =
                        new AbstractList<>() {
                            public int size() {
                                return width;
                            }

                            public Long get(int index) {
                                Objects.checkIndex(index, width);
                                return mapping[index] < 0 ? 0L : (long) counts[mapping[index]];
                            }
                        };
                rows.add(new Row(counts, wins, ties, global));
            }
            lookups.put(h.publicHistory(), new Lookup(h.activeMask(), mapping, List.copyOf(rows)));
        }
        return new SixMaxFlopPayoffView() {
            private Lookup history(String h) {
                var row = lookups.get(h);
                if (row == null) throw new IllegalArgumentException("Unknown compact history");
                return row;
            }

            public String namespace() {
                return namespace;
            }

            public int dealCount() {
                return hands.size();
            }

            public List<String> hands(int deal) {
                return hands.get(deal);
            }

            public List<Long> counts(int deal) {
                throw new IllegalArgumentException("Public history required");
            }

            public List<Long> counts(String h, int deal) {
                return history(h).rows().get(deal).globalCounts();
            }

            public String key(int observation) {
                return keys.get(observation);
            }

            public double share(int deal, int mask, int player, int observation) {
                throw new IllegalArgumentException("Public history required");
            }

            public double share(String key, int deal, int mask, int player, int observation) {
                var h = history(key);
                if (player < 0 || player >= 6 || h.mask() != mask || (mask & (1 << player)) == 0)
                    throw new IllegalArgumentException("Wrong compact active pair");
                Objects.checkIndex(observation, width);
                int index = h.globalToLocal()[observation];
                if (index < 0) throw new IllegalArgumentException("Absent compact observation");
                var row = h.rows().get(deal);
                long runouts = row.counts()[index] * 666;
                if (runouts <= 0) throw new IllegalArgumentException("Absent compact deal support");
                double first = (row.wins()[index] + .5 * row.ties()[index]) / runouts;
                return player == Integer.numberOfTrailingZeros(mask) ? first : 1 - first;
            }
        };
    }

    public static void write(Path path, Verified verified) throws Exception {
        if (Files.exists(path))
            throw new IllegalArgumentException("Compact output must be a new path");
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(verified.artifact()).getBytes(StandardCharsets.UTF_8),
                MAX_BYTES);
    }

    public static Verified replay(Path path, SixMaxHistoryPhysicalPayoffTable.Verified original)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES),
                                Artifact.class);
        if (!saved.originalPayoffHash()
                .equals(SixMaxHistoryPhysicalPayoffTable.hash(original.artifact())))
            throw new IllegalArgumentException("Compact predecessor differs");
        var expected = project(original);
        if (!saved.equals(expected.artifact()) || !restore(saved).equals(original.artifact()))
            throw new IllegalArgumentException("Exact compact storage replay differs");
        return expected;
    }
}
