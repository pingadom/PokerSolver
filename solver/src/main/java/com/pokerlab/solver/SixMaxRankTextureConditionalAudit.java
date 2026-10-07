package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Read-only local best responses in the declared rank/texture game, with independently evaluated
 * parent-game unilateral witnesses. Hidden posterior marginals are offline diagnostics only.
 */
public final class SixMaxRankTextureConditionalAudit {
    public static final String SCHEMA = "six-max-rank-texture-conditional-audit/v1";
    public static final String SCOPE =
            "REACHED_RANK_TEXTURE_DECISIONS_WITH_FIXED_PREFLOP_UNILATERAL_PARENT_WITNESSES";
    static final int MAX_REPORT_BYTES = 32 * 1024 * 1024;
    // Descriptive thresholds, deliberately not publication or trainer admission rules.
    public static final double GAP_THRESHOLD_BB = .01;
    public static final double MARGINAL_THRESHOLD = .05;

    public record SignalAudit(
            SixMaxRankTexturePayoffTable.Signal signal,
            String status,
            double signalProbabilityGivenHistory,
            int posteriorPrivateDeals,
            Map<String, Double> firstMarginal,
            Map<String, Double> secondMarginal,
            int firstCombosAtFivePercent,
            int secondCombosAtFivePercent,
            SixMaxConnectedPreflopAudit.Quality quality) {
        public SignalAudit {
            firstMarginal = Map.copyOf(firstMarginal);
            secondMarginal = Map.copyOf(secondMarginal);
        }
    }

    public record HistoryAudit(
            List<PublicAction> history,
            String status,
            Seat firstToAct,
            Seat secondToAct,
            double historyProbability,
            double signalProbabilitiesSum,
            List<SignalAudit> signals) {
        public HistoryAudit {
            history = List.copyOf(history);
            signals = List.copyOf(signals);
        }
    }

    public record Summary(
            int auditedSignals,
            int zeroReachHistories,
            int signalsWithoutReachedPrivateSupport,
            int signalsAboveGapThreshold,
            int bothPlayersTwoCombosAtFivePercent,
            double largestConditionalGapBb,
            double largestDiverseConditionalGapBb) {}

    public record ParentWitness(
            SixMaxConnectedPreflopAudit.Quality parentQuality,
            List<Double> reachWeightedLocalGainsBb,
            double reachWeightedLocalNashConvBb,
            List<Double> embeddedPostflopResponseUtilitiesBb,
            List<Double> embeddedPostflopResponseGainsBb,
            List<Double> embeddingErrorsBb,
            List<Integer> responseInformationSets,
            List<String> responseActionHashes) {
        public ParentWitness {
            reachWeightedLocalGainsBb = List.copyOf(reachWeightedLocalGainsBb);
            embeddedPostflopResponseUtilitiesBb = List.copyOf(embeddedPostflopResponseUtilitiesBb);
            embeddedPostflopResponseGainsBb = List.copyOf(embeddedPostflopResponseGainsBb);
            embeddingErrorsBb = List.copyOf(embeddingErrorsBb);
            responseInformationSets = List.copyOf(responseInformationSets);
            responseActionHashes = List.copyOf(responseActionHashes);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            String qualityScope,
            String model,
            String sourcePackHash,
            String sourceSpotHash,
            String payoffTableHash,
            String gameHash,
            String solutionHash,
            String algorithm,
            String chanceTraversal,
            int iterations,
            long completeTreeStates,
            double descriptiveGapThresholdBb,
            double descriptiveMarginalThreshold,
            List<HistoryAudit> histories,
            Summary summary,
            ParentWitness parentWitness) {
        public Report {
            histories = List.copyOf(histories);
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || !SCOPE.equals(qualityScope)
                    || !SixMaxRankTextureStudy.MODEL.equals(model)
                    || !("CFR_PLUS".equals(algorithm)
                            || SixMaxTextureStudy.PRUNED_ALGORITHM.equals(algorithm))
                    || !"EXHAUSTIVE".equals(chanceTraversal)
                    || iterations < 1
                    || iterations > SixMaxRankTextureStudy.MAX_ITERATIONS
                    || completeTreeStates < 1
                    || completeTreeStates > SixMaxRankTextureFlopGame.MAX_COMPLETE_STATES
                    || histories.isEmpty()
                    || histories.size() > SixMaxRankTextureFlopGame.MAX_SELECTED_HISTORIES
                    || descriptiveGapThresholdBb != GAP_THRESHOLD_BB
                    || descriptiveMarginalThreshold != MARGINAL_THRESHOLD)
                throw new IllegalArgumentException("Unsupported conditional audit identity or cap");
            for (String hash :
                    List.of(
                            sourcePackHash,
                            sourceSpotHash,
                            payoffTableHash,
                            gameHash,
                            solutionHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid conditional audit hash");
            java.util.Objects.requireNonNull(summary, "summary");
            java.util.Objects.requireNonNull(parentWitness, "parentWitness");
        }
    }

    private SixMaxRankTextureConditionalAudit() {}

    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            SixMaxRankTextureStudy.Checkpoint cp)
            throws Exception {
        var game = SixMaxRankTextureStudy.rebuild(source, table, cp);
        var diagnostics = SixMaxFlopConditionalDiagnostics.assess(game.core(), cp.solution());
        var histories =
                diagnostics.histories().stream()
                        .map(
                                h ->
                                        new HistoryAudit(
                                                h.history(),
                                                h.status(),
                                                h.firstToAct(),
                                                h.secondToAct(),
                                                h.historyProbability(),
                                                h.signalProbabilitiesSum(),
                                                h.signals().stream()
                                                        .map(
                                                                s ->
                                                                        new SignalAudit(
                                                                                table.signals()
                                                                                        .get(
                                                                                                s
                                                                                                        .observation()),
                                                                                s.status(),
                                                                                s
                                                                                        .signalProbabilityGivenHistory(),
                                                                                s
                                                                                        .posteriorPrivateDeals(),
                                                                                s.firstMarginal(),
                                                                                s.secondMarginal(),
                                                                                s
                                                                                        .firstCombosAtFivePercent(),
                                                                                s
                                                                                        .secondCombosAtFivePercent(),
                                                                                s.quality()))
                                                        .toList()))
                        .toList();
        return new Report(
                SCHEMA,
                "VALIDATION_ONLY",
                SCOPE,
                cp.model(),
                cp.sourcePackHash(),
                cp.sourceSpotHash(),
                cp.payoffTableHash(),
                cp.gameHash(),
                cp.solutionHash(),
                cp.algorithm(),
                cp.chanceTraversal(),
                cp.solution().iterations(),
                cp.completeTreeStates(),
                GAP_THRESHOLD_BB,
                MARGINAL_THRESHOLD,
                histories,
                diagnostics.summary(),
                diagnostics.parentWitness());
    }

    public static void write(Path output, Report report) throws Exception {
        SixMaxRankTexturePayoffTable.writeBytes(
                output,
                SixMaxTextureStudy.json(report).getBytes(StandardCharsets.UTF_8),
                MAX_REPORT_BYTES);
    }

    /**
     * Strict bounded load followed by complete deterministic recomputation, never trusting claims.
     */
    public static Report replay(
            Path input,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            SixMaxRankTextureStudy.Checkpoint cp)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(input, MAX_REPORT_BYTES),
                                Report.class);
        var expected = assess(source, table, cp);
        if (!expected.equals(saved))
            throw new IllegalArgumentException("Conditional audit replay differs");
        return expected;
    }

    static record ConditionalGame(
            MultiPlayerCfrGame<SixMaxRankTextureFlopGame.State> parent,
            List<ChanceOutcome<SixMaxRankTextureFlopGame.State>> roots)
            implements MultiPlayerCfrGame<SixMaxRankTextureFlopGame.State> {
        ConditionalGame {
            roots = List.copyOf(roots);
        }

        @Override
        public int playerCount() {
            return 6;
        }

        @Override
        public SixMaxRankTextureFlopGame.State initialState() {
            return parent.initialState();
        }

        @Override
        public boolean isTerminal(SixMaxRankTextureFlopGame.State state) {
            return !state.equals(initialState()) && parent.isTerminal(state);
        }

        @Override
        public int currentPlayer(SixMaxRankTextureFlopGame.State state) {
            return state.equals(initialState()) ? -1 : parent.currentPlayer(state);
        }

        @Override
        public List<String> legalActions(SixMaxRankTextureFlopGame.State state) {
            return state.equals(initialState()) ? List.of() : parent.legalActions(state);
        }

        @Override
        public String informationSet(SixMaxRankTextureFlopGame.State state) {
            return parent.informationSet(state);
        }

        @Override
        public SixMaxRankTextureFlopGame.State afterAction(
                SixMaxRankTextureFlopGame.State state, String action) {
            return parent.afterAction(state, action);
        }

        @Override
        public List<ChanceOutcome<SixMaxRankTextureFlopGame.State>> chanceOutcomes(
                SixMaxRankTextureFlopGame.State state) {
            if (!state.equals(initialState()))
                throw new IllegalArgumentException("Only posterior root is a chance node");
            return roots;
        }

        @Override
        public double[] terminalUtilities(SixMaxRankTextureFlopGame.State state) {
            return parent.terminalUtilities(state);
        }

        @Override
        public OptionalDouble inactivePlayerUtility(
                SixMaxRankTextureFlopGame.State state, int player) {
            return state.equals(initialState())
                    ? OptionalDouble.empty()
                    : parent.inactivePlayerUtility(state, player);
        }
    }
}
