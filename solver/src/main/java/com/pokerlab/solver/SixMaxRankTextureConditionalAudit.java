package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxPreflopResearchTrainer.PublicAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

    private record Posterior(
            double signalProbability,
            List<ChanceOutcome<SixMaxRankTextureFlopGame.State>> roots,
            Map<String, Double> firstMarginal,
            Map<String, Double> secondMarginal) {}

    private SixMaxRankTextureConditionalAudit() {}

    public static Report assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact table,
            SixMaxRankTextureStudy.Checkpoint cp)
            throws Exception {
        var game = SixMaxRankTextureStudy.rebuild(source, table, cp);
        var policy = cp.solution();
        var preflop = SixMaxPreflopContinuationFeedback.preflopPolicy(policy);
        var responses = new ArrayList<Map<String, String>>();
        for (int player = 0; player < 6; player++) responses.add(new LinkedHashMap<>());
        double[] weighted = new double[6];
        var histories = new ArrayList<HistoryAudit>();
        int audited = 0, zero = 0, unsupported = 0, above = 0, diverse = 0;
        double largest = 0, diverseLargest = 0;
        for (var selection : game.selections()) {
            if (!SixMaxTextureConditionalAudit.hasReach(
                    game.sourceGame(), preflop, selection.history())) {
                zero++;
                histories.add(
                        new HistoryAudit(
                                selection.history(),
                                "ZERO_POLICY_REACH",
                                null,
                                null,
                                0,
                                0,
                                List.of()));
                continue;
            }
            var transition =
                    new SixMaxPolicyFlopTransition(game.sourceGame(), preflop, selection.history());
            requireNormal(transition.reachProbability());
            for (var deal : transition.deals()) requireNormal(deal.probability());
            var signals = new ArrayList<SignalAudit>();
            double probabilitySum = 0;
            for (int signal = 0; signal < table.signals().size(); signal++) {
                var posterior = posterior(game, transition, signal);
                double probability = posterior.signalProbability();
                probabilitySum += probability;
                if (posterior.roots().isEmpty()) {
                    unsupported++;
                    signals.add(
                            new SignalAudit(
                                    table.signals().get(signal),
                                    "NO_REACHED_PRIVATE_SUPPORT",
                                    0,
                                    0,
                                    Map.of(),
                                    Map.of(),
                                    0,
                                    0,
                                    null));
                    continue;
                }
                var response =
                        MultiPlayerInformationSetBestResponse.assess(
                                new ConditionalGame(game, posterior.roots()), policy);
                double weight = transition.reachProbability() * probability;
                requireNormal(weight);
                for (int player = 0; player < 6; player++) {
                    weighted[player] += weight * response.deviationGainsBb().get(player);
                    for (var action : response.responseActions().get(player).entrySet()) {
                        String key = player + ":" + action.getKey();
                        // No preflop action may change, and cases must have disjoint information
                        // sets.
                        if (!key.contains(":postflop:rank-texture:")
                                || responses.get(player).putIfAbsent(key, action.getValue())
                                        != null)
                            throw new IllegalStateException(
                                    "Conditional responses overlap or change preflop");
                    }
                }
                var quality = SixMaxConnectedPreflopAudit.Quality.of(response);
                int first = material(posterior.firstMarginal());
                int second = material(posterior.secondMarginal());
                audited++;
                if (quality.nashConvBb() > GAP_THRESHOLD_BB) above++;
                largest = Math.max(largest, quality.nashConvBb());
                if (first >= 2 && second >= 2) {
                    diverse++;
                    diverseLargest = Math.max(diverseLargest, quality.nashConvBb());
                }
                signals.add(
                        new SignalAudit(
                                table.signals().get(signal),
                                "AUDITED",
                                probability,
                                posterior.roots().size(),
                                posterior.firstMarginal(),
                                posterior.secondMarginal(),
                                first,
                                second,
                                quality));
            }
            if (Math.abs(probabilitySum - 1) > 1e-12)
                throw new IllegalStateException(
                        "Conditional signals do not partition reached chance");
            histories.add(
                    new HistoryAudit(
                            selection.history(),
                            "AUDITED",
                            transition.firstToAct(),
                            transition.secondToAct(),
                            transition.reachProbability(),
                            probabilitySum,
                            signals));
        }
        var witness = embed(game, policy, weighted, responses);
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
                policy.iterations(),
                cp.completeTreeStates(),
                GAP_THRESHOLD_BB,
                MARGINAL_THRESHOLD,
                histories,
                new Summary(audited, zero, unsupported, above, diverse, largest, diverseLargest),
                witness);
    }

    private static ParentWitness embed(
            SixMaxRankTextureFlopGame game,
            CfrSolution policy,
            double[] weighted,
            List<Map<String, String>> responses)
            throws Exception {
        var parent = MultiPlayerInformationSetBestResponse.assess(game, policy);
        var weightedGains = new ArrayList<Double>();
        var utilities = new ArrayList<Double>();
        var gains = new ArrayList<Double>();
        var errors = new ArrayList<Double>();
        var counts = new ArrayList<Integer>();
        var hashes = new ArrayList<String>();
        double total = 0;
        for (int player = 0; player < 6; player++) {
            var response = responses.get(player);
            var rows = new LinkedHashMap<>(policy.strategy());
            for (var action : response.entrySet()) {
                var original = rows.get(action.getKey());
                if (original == null || !original.containsKey(action.getValue()))
                    throw new IllegalStateException(
                            "Conditional response has a foreign or illegal action");
                var pure = new LinkedHashMap<String, Double>();
                original.forEach(
                        (key, value) -> pure.put(key, key.equals(action.getValue()) ? 1.0 : 0.0));
                rows.put(action.getKey(), pure);
            }
            double value =
                    response.isEmpty()
                            ? parent.profileUtilitiesBb().get(player)
                            : MultiPlayerStrategyEvaluator.utilities(
                                    game, new CfrSolution(policy.iterations(), rows))[player];
            double gain = value - parent.profileUtilitiesBb().get(player);
            double error = gain - weighted[player];
            if (Math.abs(error) > 1e-9
                    || gain < -1e-9
                    || gain > parent.deviationGainsBb().get(player) + 1e-9)
                throw new IllegalStateException(
                        "Embedded local responses disagree with parent bounds");
            weightedGains.add(weighted[player]);
            total += weighted[player];
            utilities.add(value);
            gains.add(gain);
            errors.add(error);
            counts.add(response.size());
            hashes.add(
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    SixMaxTexturePayoffTable.mapper()
                                                            .writeValueAsBytes(response))));
        }
        return new ParentWitness(
                SixMaxConnectedPreflopAudit.Quality.of(parent),
                weightedGains,
                total,
                utilities,
                gains,
                errors,
                counts,
                hashes);
    }

    private static Posterior posterior(
            SixMaxRankTextureFlopGame game, SixMaxPolicyFlopTransition transition, int signal) {
        var table = game.payoffTable();
        var roots = game.chanceOutcomes(game.initialState());
        var states = new ArrayList<ChanceOutcome<SixMaxRankTextureFlopGame.State>>();
        var first = new LinkedHashMap<String, Double>();
        var second = new LinkedHashMap<String, Double>();
        double probability = 0;
        for (var posterior : transition.deals()) {
            var keys = posterior.hands().stream().map(WeightedCombo::key).toList();
            int deal = -1;
            for (int i = 0; i < table.deals().size(); i++)
                if (table.deals().get(i).hands().equals(keys)) {
                    deal = i;
                    break;
                }
            if (deal < 0)
                throw new IllegalArgumentException("Conditional table private support differs");
            long flops = table.deals().get(deal).flopCounts().get(signal);
            if (flops == 0) continue;
            double mass =
                    posterior.probability() * flops / SixMaxPolicyFlopTransition.FLOPS_PER_DEAL;
            requireNormal(mass);
            var state = roots.get(deal).state();
            for (var action : transition.history())
                state = game.afterAction(state, action.action());
            states.add(
                    new ChanceOutcome<>(
                            new SixMaxRankTextureFlopGame.State(state.preflop(), signal, ""),
                            mass));
            probability += mass;
            first.merge(keys.get(transition.firstToAct().ordinal()), mass, Double::sum);
            second.merge(keys.get(transition.secondToAct().ordinal()), mass, Double::sum);
        }
        if (probability == 0) return new Posterior(0, List.of(), Map.of(), Map.of());
        requireNormal(probability);
        double normalizer = probability;
        var normalized =
                states.stream()
                        .map(s -> new ChanceOutcome<>(s.state(), s.probability() / normalizer))
                        .toList();
        first.replaceAll((key, value) -> value / normalizer);
        second.replaceAll((key, value) -> value / normalizer);
        return new Posterior(probability, normalized, first, second);
    }

    private static void requireNormal(double probability) {
        if (!Double.isFinite(probability) || probability < Double.MIN_NORMAL)
            throw new IllegalArgumentException(
                    "Conditional reach underflow requires a log-space audit");
    }

    private static int material(Map<String, Double> marginal) {
        return (int) marginal.values().stream().filter(mass -> mass >= MARGINAL_THRESHOLD).count();
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
            SixMaxRankTextureFlopGame parent,
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
