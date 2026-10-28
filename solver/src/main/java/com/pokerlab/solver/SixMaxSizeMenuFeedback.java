package com.pokerlab.solver;

import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.Phase;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Fresh-policy qualification on the jointly solved size-menu game, with no public admission. */
public final class SixMaxSizeMenuFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-size-menu-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-size-menu-feedback-report/v1";
    public static final String STATUS = "SIZE_MENU_POLICY_QUALIFICATION_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxSizeMenuConditionalStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSizeMenuConditionalStudy.Request conditional,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion) || conditional == null)
                throw new IllegalArgumentException("Invalid size-menu feedback request");
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            SixMaxSizeMenuConditionalStudy.Report conditional,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Check> references,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Report {
            moves = List.copyOf(moves);
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || conditional == null
                    || primary == null
                    || !request.conditional().equals(conditional.request())
                    || !moves.equals(
                            SixMaxSizeMenuFeedback.moves(conditional, request.maximumLossBb()))
                    || primary.iterations() != conditional.parent().candidate().iterations()
                    || !primary.solutionHash().equals(conditional.parent().solutionHash())
                    || references.size() != 2
                    || !references.stream()
                            .map(Check::iterations)
                            .toList()
                            .equals(List.of(80_000, 160_000))
                    || !references.stream()
                            .map(Check::solutionHash)
                            .toList()
                            .equals(
                                    conditional.parent().references().stream()
                                            .map(r -> r.policyHash())
                                            .toList())
                    || !rejectionReasons.equals(reasons(moves, primary, references))
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Invalid size-menu qualification identity");
        }
    }

    public static final class QualifiedFeedback {
        private final String reportHash;

        private QualifiedFeedback(Report report) throws Exception {
            reportHash = SixMaxHeadsUpPreflopGame.hash(report);
        }

        public String reportHash() {
            return reportHash;
        }
    }

    public static final class Result {
        private final Report report;
        private final QualifiedFeedback qualified;

        private Result(Report report) throws Exception {
            this.report = report;
            qualified = report.qualifiedForOfflineDrill() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(qualified);
        }
    }

    private SixMaxSizeMenuFeedback() {}

    static List<SixMaxSuppliedIntervalFeedback.Move> moves(
            SixMaxSizeMenuConditionalStudy.Report source, double limit) {
        return source.actions().stream()
                .map(
                        a ->
                                new SixMaxSuppliedIntervalFeedback.Move(
                                        a.action(),
                                        a.lowerUtilityBb(),
                                        a.upperUtilityBb(),
                                        a.lowerLossBb(),
                                        a.upperLossBb(),
                                        SixMaxSuppliedIntervalFeedback.classify(
                                                a.lowerLossBb(), a.upperLossBb(), limit)))
                .toList();
    }

    static List<String> reasons(
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Check> references) {
        var failures = new TreeSet<String>();
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .WITHIN_LIMIT))
            failures.add("NO_ROBUST_MOVE_WITHIN_LIMIT");
        if (moves.stream()
                .noneMatch(
                        m ->
                                m.classification()
                                        == SixMaxSuppliedIntervalFeedback.Classification
                                                .OUTSIDE_LIMIT))
            failures.add("NO_ROBUST_MOVE_OUTSIDE_LIMIT");
        primary.failures().forEach(f -> failures.add("PRIMARY:" + f));
        for (var check : references)
            check.failures().forEach(f -> failures.add(check.iterations() + ":" + f));
        return List.copyOf(failures);
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var parent = request.conditional().parent();
        var game = new SixMaxSuppliedFlopSizeMenuGame(parent.input(), parent.flop(), parent.menu());
        return solve(request, game);
    }

    static Result solve(Request request, SixMaxSuppliedFlopSizeMenuGame game) throws Exception {
        // This owned solve already creates both fresh reference policies. Never train them again
        // here.
        var owned = SixMaxSizeMenuConditionalStudy.solve(request.conditional(), game);
        return qualify(request, game, owned);
    }

    static Result qualify(
            Request request,
            SixMaxSuppliedFlopSizeMenuGame game,
            SixMaxSizeMenuConditionalStudy.Result owned)
            throws Exception {
        Objects.requireNonNull(owned);
        var source = owned.report();
        if (!request.conditional().equals(source.request())
                || !game.binding().equals(source.parent().binding()))
            throw new IllegalArgumentException("Owned size-menu qualification lineage differs");
        var moves = moves(source, request.maximumLossBb());
        var primary = assess(game, source, source.parent().candidate());
        var references =
                source.parent().references().stream()
                        .map(r -> assess(game, source, r.policy()))
                        .toList();
        var failures = reasons(moves, primary, references);
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        source,
                        moves,
                        primary,
                        references,
                        failures.isEmpty(),
                        failures));
    }

    private static double product(double mass, double probability) {
        if (!Double.isFinite(probability) || probability < 0 || probability > 1)
            throw new IllegalArgumentException("Invalid size-menu path probability");
        double next = mass * probability;
        if (mass > 0 && probability > 0 && next == 0)
            throw new IllegalArgumentException("Positive path underflow");
        return next;
    }

    private static void validatePolicy(
            SixMaxSizeMenuConditionalStudy.Report source, CfrSolution policy) {
        var expected = source.parent().candidate().strategy();
        if (!policy.strategy().keySet().equals(expected.keySet()))
            throw new IllegalArgumentException("Incomplete size-menu policy");
        for (var entry : expected.entrySet()) {
            var row = policy.strategy().get(entry.getKey());
            if (!row.keySet().equals(entry.getValue().keySet())
                    || row.values().stream()
                            .anyMatch(p -> p == null || !Double.isFinite(p) || p < 0 || p > 1)
                    || Math.abs(row.values().stream().mapToDouble(Double::doubleValue).sum() - 1)
                            > 1e-9)
                throw new IllegalArgumentException("Invalid size-menu policy row");
        }
    }

    static Check assess(
            SixMaxSuppliedFlopSizeMenuGame game,
            SixMaxSizeMenuConditionalStudy.Report source,
            CfrSolution policy) {
        if (!game.binding().equals(source.parent().binding()))
            throw new IllegalArgumentException("Size-menu assessment game differs");
        validatePolicy(source, policy);
        var q = source.request();
        var handoff =
                game.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(q.publicPreflopHistory()))
                        .findFirst()
                        .orElseThrow();
        var allRoots = new ArrayList<ChanceOutcome<State>>();
        var raw = new ArrayList<SixMaxSuppliedFlopBettingStudy.WorldMass>();
        var selectedStates = new ArrayList<State>();
        var selectedMasses = new ArrayList<Double>();
        double reachedBoundary = 0, actual = 0, total = 0;
        for (var world : handoff.worlds()) {
            double cf =
                    product(
                            world.originalJointProbability(),
                            world.physicalFlopProbabilityGivenDeal());
            double reached = cf;
            var parent = new SixMaxHeadsUpPreflopGame.State(world.originalDealIndex(), "");
            for (String action : handoff.continuationActions()) {
                int actor = game.source().currentPlayer(parent);
                double p =
                        policy.at(
                                        actor,
                                        SixMaxSuppliedFlopSizeMenuGame.PREFLOP_KEY
                                                + game.source().informationSet(parent))
                                .get(action);
                reached = product(reached, p);
                if (actor != q.hero()) cf = product(cf, p);
                parent = game.source().afterAction(parent, action);
            }
            var state =
                    new State(world.originalDealIndex(), handoff.publicHistory(), Phase.FLOP, "");
            if (reached > 0) {
                raw.add(
                        new SixMaxSuppliedFlopBettingStudy.WorldMass(
                                world.originalDealIndex(), reached));
                allRoots.add(new ChanceOutcome<>(state, reached));
                reachedBoundary += reached;
            }
            if (world.sixDealtCombos().get(q.hero()).equals(q.heroCards())) {
                if (!source.informationSet().equals(q.hero() + ":" + game.informationSet(state)))
                    throw new IllegalArgumentException("Owned question identity differs");
                selectedStates.add(state);
                selectedMasses.add(cf);
                actual += reached;
                total += cf;
            }
        }
        double physical = handoff.physicalFlopMass();
        var reachedWorlds = new ArrayList<SixMaxSuppliedFlopBettingStudy.ReachedWorld>();
        MultiPlayerInformationSetBestResponse.Report localQuality = null;
        boolean supported =
                reachedBoundary / physical
                        >= SixMaxSuppliedFlopBettingStudy.MIN_CONDITIONAL_BOUNDARY_REACH;
        if (supported) {
            var normalized = new ArrayList<ChanceOutcome<State>>();
            for (int i = 0; i < allRoots.size(); i++) {
                var root = allRoots.get(i);
                double p = root.probability() / reachedBoundary;
                normalized.add(new ChanceOutcome<>(root.state(), p));
                reachedWorlds.add(
                        new SixMaxSuppliedFlopBettingStudy.ReachedWorld(
                                raw.get(i).originalDealIndex(), root.probability(), p));
            }
            localQuality =
                    MultiPlayerInformationSetBestResponse.assess(
                            new ForcedRoot(game, List.copyOf(normalized)), policy);
        }
        var local =
                new SixMaxSuppliedFlopBettingStudy.LocalAudit(
                        SixMaxSuppliedFlopBettingStudy.LOCAL_SCOPE,
                        handoff.publicHistory(),
                        reachedBoundary,
                        physical,
                        reachedBoundary / physical,
                        SixMaxSuppliedFlopBettingStudy.MIN_CONDITIONAL_BOUNDARY_REACH,
                        supported
                                ? SixMaxSuppliedFlopBettingStudy.Conditioning
                                        .ABOVE_CANDIDATE_REACH_FLOOR
                                : SixMaxSuppliedFlopBettingStudy.Conditioning
                                        .BELOW_CANDIDATE_REACH_FLOOR,
                        raw,
                        reachedWorlds,
                        localQuality);
        var quality = MultiPlayerInformationSetBestResponse.assess(game, policy);
        double br = quality.bestResponseUtilitiesBb().get(q.hero());
        double upper = source.actions().getFirst().diagnostic().globalHeroUpperValue();
        double margin = upper + SixMaxSuppliedFlopActionBounds.SECURITY_SLACK - br;
        boolean included = margin >= -BoundedLinearProgram.CERTIFICATE_TOLERANCE;
        var failures = new ArrayList<String>();
        if (!included) failures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
        if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("FULL_PARENT_GAP");
        if (localQuality == null
                || localQuality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("REACHED_FLOP_GAP");
        if (actual / physical < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_ACTUAL_QUESTION_REACH");
        if (total / physical < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH");
        var values = new TreeMap<String, Double>();
        var losses = new TreeMap<String, Double>();
        Double regret = null, violation = null;
        boolean checked = included && total > 0;
        if (total > 0) {
            for (var action : source.actions()) {
                var roots = new ArrayList<ChanceOutcome<State>>();
                for (int i = 0; i < selectedStates.size(); i++)
                    if (selectedMasses.get(i) > 0)
                        roots.add(
                                new ChanceOutcome<>(
                                        game.afterAction(selectedStates.get(i), action.action()),
                                        selectedMasses.get(i) / total));
                values.put(
                        action.action(),
                        MultiPlayerInformationSetBestResponse.assess(
                                        new ForcedRoot(game, List.copyOf(roots)), policy)
                                .bestResponseUtilitiesBb()
                                .get(q.hero()));
            }
            double best = Collections.max(values.values());
            values.forEach((a, v) -> losses.put(a, Math.max(0, best - v)));
            var row = policy.at(q.hero(), game.informationSet(selectedStates.getFirst()));
            regret =
                    losses.entrySet().stream()
                            .mapToDouble(e -> row.get(e.getKey()) * e.getValue())
                            .sum();
            if (regret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                failures.add("SELECTED_DECISION_REGRET");
            if (checked) {
                double worst = 0;
                for (var a : source.actions())
                    worst =
                            Math.max(
                                    worst,
                                    Math.max(
                                            Math.max(
                                                    a.lowerUtilityBb() - values.get(a.action()),
                                                    values.get(a.action()) - a.upperUtilityBb()),
                                            Math.max(
                                                    a.lowerLossBb() - losses.get(a.action()),
                                                    losses.get(a.action()) - a.upperLossBb())));
                violation = worst;
                if (worst > BoundedLinearProgram.CERTIFICATE_TOLERANCE)
                    failures.add("REFERENCE_OUTSIDE_DECISION_BOUNDS");
            }
        }
        return new Check(
                policy.iterations(),
                SixMaxConnectedPostflopAudit.solutionHash(policy),
                quality,
                local,
                actual,
                actual / physical,
                total / physical,
                br,
                upper,
                margin,
                included,
                checked,
                violation,
                values,
                losses,
                regret,
                failures);
    }

    private record ForcedRoot(SixMaxSuppliedFlopSizeMenuGame game, List<ChanceOutcome<State>> roots)
            implements MultiPlayerCfrGame<State> {
        private static final State ROOT = new State(-201, "", Phase.FLOP, "size-menu-quality-root");

        public int playerCount() {
            return game.playerCount();
        }

        public State initialState() {
            return ROOT;
        }

        public boolean isTerminal(State state) {
            return !ROOT.equals(state) && game.isTerminal(state);
        }

        public double[] terminalUtilities(State state) {
            return game.terminalUtilities(state);
        }

        public int currentPlayer(State state) {
            return ROOT.equals(state) ? -1 : game.currentPlayer(state);
        }

        public List<String> legalActions(State state) {
            return game.legalActions(state);
        }

        public String informationSet(State state) {
            return game.informationSet(state);
        }

        public State afterAction(State state, String action) {
            return game.afterAction(state, action);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State state) {
            if (!ROOT.equals(state))
                throw new IllegalArgumentException("Owned quality root required");
            return roots;
        }
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded feedback cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored feedback cap exceeded");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path output) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        var request = readRequest(input);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(output, MAX_BYTES),
                                Report.class);
        if (!saved.request().equals(request))
            throw new IllegalArgumentException("Feedback request differs");
        var owned = solve(request);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Full size-menu qualification replay differs");
        return owned;
    }
}
