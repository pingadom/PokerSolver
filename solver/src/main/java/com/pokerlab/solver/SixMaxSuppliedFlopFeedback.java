package com.pokerlab.solver;

import com.pokerlab.solver.PreflopAllInSpot.Seat;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Owned qualification of one initial physical-flop question, separate from public admission. */
public final class SixMaxSuppliedFlopFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-supplied-flop-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-supplied-flop-feedback-report/v1";
    public static final String STATUS = "INITIAL_PHYSICAL_FLOP_INTERVAL_FEEDBACK_RESEARCH_ONLY";
    public static final String EV_SCOPE =
            "ABSOLUTE_TERMINAL_PROFIT_OPTIMAL_FUTURE_CONTINUATION_BB/v1";
    public static final int MAX_BYTES = SixMaxSuppliedFlopBettingStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopBettingStudy.Request connected,
            int hero,
            String informationSet,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || connected == null
                    || hero < 0
                    || hero >= 6
                    || informationSet == null
                    || informationSet.isBlank()
                    || informationSet.length() > 4096)
                throw new IllegalArgumentException("Invalid initial flop feedback request");
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
        }
    }

    public record Player(Seat seat, String status, double committedBb, double remainingStackBb) {}

    /** Every field comes from owned handoff states, never from parsing an information-set key. */
    public record Context(
            Seat hero,
            String heroCards,
            List<String> board,
            String publicHistory,
            List<SixMaxPreflopBetting.Move> preflopHistory,
            double potBb,
            double toCallBb,
            double betSizeBb,
            List<Player> players,
            List<String> legalActions) {
        public Context {
            board = List.copyOf(board);
            preflopHistory = List.copyOf(preflopHistory);
            players = List.copyOf(players);
            legalActions = List.copyOf(legalActions);
        }
    }

    public record Check(
            int iterations,
            String solutionHash,
            MultiPlayerInformationSetBestResponse.Report fullParentQuality,
            SixMaxSuppliedFlopBettingStudy.LocalAudit reachedFlopQuality,
            double actualQuestionRootReach,
            double actualQuestionReachGivenPhysicalFlop,
            double counterfactualQuestionReachGivenPhysicalFlop,
            double globalHeroBestResponseBb,
            double globalHeroUpperBb,
            double inclusionMarginBb,
            boolean insideDeclaredFace,
            boolean boundsChecked,
            Double maximumBoundViolationBb,
            Map<String, Double> actionUtilitiesBb,
            Map<String, Double> actionLossBb,
            Double decisionRegretBb,
            List<String> failures) {
        public Check {
            actionUtilitiesBb = Collections.unmodifiableMap(new TreeMap<>(actionUtilitiesBb));
            actionLossBb = Collections.unmodifiableMap(new TreeMap<>(actionLossBb));
            failures = List.copyOf(failures);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            String evScope,
            String reachUnits,
            SixMaxSuppliedFlopActionBounds.Report bounds,
            Context context,
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
                    || bounds == null
                    || context == null
                    || primary == null
                    || !EV_SCOPE.equals(evScope)
                    || !SixMaxSuppliedFlopActionBounds.REACH_UNITS.equals(reachUnits)
                    || !request.connected().equals(bounds.parent().request())
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty()
                    || !references.stream()
                            .map(Check::iterations)
                            .toList()
                            .equals(List.of(2000, 4000)))
                throw new IllegalArgumentException("Invalid flop feedback identity");
        }
    }

    /** A parsed report or a caller-selected hash cannot construct this authority. */
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
        private final QualifiedFeedback feedback;

        private Result(Report report) throws Exception {
            this.report = report;
            feedback = report.qualifiedForOfflineDrill() ? new QualifiedFeedback(report) : null;
        }

        public Report report() {
            return report;
        }

        public Optional<QualifiedFeedback> qualifiedFeedback() {
            return Optional.ofNullable(feedback);
        }
    }

    private SixMaxSuppliedFlopFeedback() {}

    public static Result solve(Request request) throws Exception {
        var connected = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        connected.input(), connected.flop(), connected.betPotFraction());
        return qualify(request, game, SixMaxSuppliedFlopActionBounds.solve(connected, game));
    }

    // Only an owned bounds Result can enter this boundary; DTO reports are never accepted.
    static Result qualify(
            Request request,
            SixMaxSuppliedFlopBettingGame game,
            SixMaxSuppliedFlopActionBounds.Result owned)
            throws Exception {
        var bounds = owned.report();
        if (!request.connected().equals(bounds.parent().request())
                || !game.binding().equals(bounds.parent().binding()))
            throw new IllegalArgumentException("Owned flop feedback lineage differs");
        var decision =
                bounds.decisions().stream()
                        .filter(
                                d ->
                                        d.hero() == request.hero()
                                                && d.informationSet()
                                                        .equals(request.informationSet()))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Unsupported initial flop question"));
        var handoff = game.handoffs().getFirst();
        var states = new ArrayList<State>();
        String cards = null;
        for (var world : handoff.worlds()) {
            var state =
                    new State(
                            world.originalDealIndex(),
                            handoff.publicHistory(),
                            SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                            "");
            if ((game.currentPlayer(state) + ":" + game.informationSet(state))
                    .equals(request.informationSet())) {
                states.add(state);
                String found = world.sixDealtCombos().get(request.hero());
                if (cards != null && !cards.equals(found))
                    throw new IllegalStateException("Inconsistent owned hero cards");
                cards = found;
            }
        }
        if (states.isEmpty() || game.currentPlayer(states.getFirst()) != request.hero())
            throw new IllegalArgumentException("Initial owned question required");
        var legal = game.legalActions(states.getFirst());
        if (!legal.equals(
                decision.actions().stream()
                        .map(SixMaxSuppliedFlopActionBounds.Action::action)
                        .toList())) throw new IllegalStateException("Owned action menu differs");
        var players = new ArrayList<Player>();
        for (int i = 0; i < 6; i++)
            players.add(
                    new Player(
                            Seat.values()[i],
                            handoff.folded().get(i)
                                    ? "FOLDED"
                                    : i == request.hero()
                                            ? "ACTING"
                                            : handoff.remainingStackBb().get(i) == 0
                                                    ? "ALL_IN"
                                                    : "ACTIVE",
                            handoff.committedBb().get(i),
                            handoff.remainingStackBb().get(i)));
        double bet =
                Math.min(
                        Math.min(
                                handoff.remainingStackBb().get(handoff.firstToAct().ordinal()),
                                handoff.remainingStackBb().get(handoff.secondToAct().ordinal())),
                        Math.round(
                                        handoff.potBb()
                                                * request.connected().betPotFraction()
                                                * 1_000_000)
                                / 1_000_000.0);
        var context =
                new Context(
                        Seat.values()[request.hero()],
                        cards,
                        handoff.flop(),
                        handoff.publicHistory(),
                        handoff.completePreflopHistory(),
                        handoff.potBb(),
                        0,
                        bet,
                        players,
                        legal);
        var moves =
                decision.actions().stream()
                        .map(
                                a ->
                                        new SixMaxSuppliedIntervalFeedback.Move(
                                                a.action(),
                                                a.lowerUtilityBb(),
                                                a.upperUtilityBb(),
                                                a.lowerLossBb(),
                                                a.upperLossBb(),
                                                SixMaxSuppliedIntervalFeedback.classify(
                                                        a.lowerLossBb(),
                                                        a.upperLossBb(),
                                                        request.maximumLossBb())))
                        .toList();
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
        var primary = assess(game, handoff, request, states, decision, bounds.parent().candidate());
        primary.failures().forEach(f -> failures.add("PRIMARY:" + f));
        var refs = new ArrayList<Check>();
        for (var reference : bounds.freshReferences()) {
            var check = assess(game, handoff, request, states, decision, reference.policy());
            if (!check.fullParentQuality().equals(reference.fullParentQuality())
                    || !check.reachedFlopQuality().equals(reference.reachedFlopQuality())
                    || !check.solutionHash().equals(reference.solutionHash()))
                throw new IllegalStateException("Fresh owned reference differs");
            refs.add(check);
            check.failures().forEach(f -> failures.add(check.iterations() + ":" + f));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        EV_SCOPE,
                        SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                        bounds,
                        context,
                        moves,
                        primary,
                        refs,
                        failures.isEmpty(),
                        List.copyOf(failures)));
    }

    static Check assess(
            SixMaxSuppliedFlopBettingGame game,
            SixMaxSuppliedFlopHandoff.Audit handoff,
            Request request,
            List<State> states,
            SixMaxSuppliedFlopActionBounds.Decision decision,
            CfrSolution policy) {
        var quality = MultiPlayerInformationSetBestResponse.assess(game, policy);
        var local = SixMaxSuppliedFlopBettingStudy.localAudit(game, handoff, policy);
        var selectedIndices = new HashSet<Integer>();
        states.forEach(s -> selectedIndices.add(s.originalDealIndex()));
        double actual =
                local.rawWorldMasses().stream()
                        .filter(w -> selectedIndices.contains(w.originalDealIndex()))
                        .mapToDouble(SixMaxSuppliedFlopBettingStudy.WorldMass::jointRootFlopReach)
                        .sum();
        var masses = new ArrayList<Double>();
        for (var state : states) {
            var world =
                    handoff.worlds().stream()
                            .filter(w -> w.originalDealIndex() == state.originalDealIndex())
                            .findFirst()
                            .orElseThrow();
            double mass =
                    world.originalJointProbability() * world.physicalFlopProbabilityGivenDeal();
            var past = new SixMaxHeadsUpPreflopGame.State(world.originalDealIndex(), "");
            for (String action : handoff.continuationActions()) {
                int actor = game.source().currentPlayer(past);
                if (actor != request.hero()) {
                    double p =
                            policy.at(
                                            actor,
                                            "supplied-connected-single-flop:"
                                                    + game.source().informationSet(past))
                                    .get(action);
                    double next = mass * p;
                    if (mass > 0 && p > 0 && next == 0)
                        throw new IllegalArgumentException("Counterfactual reach underflow");
                    mass = next;
                }
                past = game.source().afterAction(past, action);
            }
            masses.add(mass);
        }
        double total = masses.stream().mapToDouble(Double::doubleValue).sum();
        var values = new TreeMap<String, Double>();
        var losses = new TreeMap<String, Double>();
        if (total > 0) {
            for (var action : decision.actions()) {
                var roots = new ArrayList<ChanceOutcome<State>>();
                for (int i = 0; i < states.size(); i++)
                    if (masses.get(i) > 0)
                        roots.add(
                                new ChanceOutcome<>(
                                        game.afterAction(states.get(i), action.action()),
                                        masses.get(i) / total));
                values.put(
                        action.action(),
                        MultiPlayerInformationSetBestResponse.assess(
                                        new ForcedRoot(game, roots), policy)
                                .bestResponseUtilitiesBb()
                                .get(request.hero()));
            }
            double best = Collections.max(values.values());
            values.forEach((a, v) -> losses.put(a, Math.max(0, best - v)));
        }
        double br = quality.bestResponseUtilitiesBb().get(request.hero());
        double upper = decision.actions().getFirst().diagnostic().globalHeroUpperValue();
        double margin = upper + SixMaxSuppliedFlopActionBounds.SECURITY_SLACK - br;
        boolean included = margin >= -BoundedLinearProgram.CERTIFICATE_TOLERANCE;
        boolean checked = included && total > 0;
        Double violation = null, regret = null;
        var failures = new ArrayList<String>();
        if (!included) failures.add("REFERENCE_OUTSIDE_DECLARED_FACE");
        if (quality.nashConvBb() > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("FULL_PARENT_GAP");
        if (local.conditionalBestResponse() == null
                || local.conditionalBestResponse().nashConvBb()
                        > SixMaxSuitDecisionStability.LOCAL_GAP_BB)
            failures.add("REACHED_FLOP_GAP");
        if (actual / handoff.physicalFlopMass()
                < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_ACTUAL_QUESTION_REACH");
        if (total / handoff.physicalFlopMass()
                < SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
            failures.add("INSUFFICIENT_COUNTERFACTUAL_QUESTION_REACH");
        if (total > 0) {
            var row = policy.at(request.hero(), game.informationSet(states.getFirst()));
            regret =
                    losses.entrySet().stream()
                            .mapToDouble(e -> row.get(e.getKey()) * e.getValue())
                            .sum();
            if (regret > SixMaxSuitDecisionStability.DECISION_TOLERANCE_BB)
                failures.add("SELECTED_DECISION_REGRET");
            if (checked) {
                double worst = 0;
                for (var a : decision.actions())
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
                actual / handoff.physicalFlopMass(),
                total / handoff.physicalFlopMass(),
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

    /**
     * Quality-only artificial chance root; the complete parent and its security face are untouched.
     */
    private record ForcedRoot(SixMaxSuppliedFlopBettingGame game, List<ChanceOutcome<State>> roots)
            implements MultiPlayerCfrGame<State> {
        private static final State ROOT =
                new State(
                        -3, "", SixMaxSuppliedFlopBettingGame.Phase.FLOP, "forced-action-quality");

        public int playerCount() {
            return game.playerCount();
        }

        public State initialState() {
            return ROOT;
        }

        public boolean isTerminal(State s) {
            return !ROOT.equals(s) && game.isTerminal(s);
        }

        public double[] terminalUtilities(State s) {
            return game.terminalUtilities(s);
        }

        public int currentPlayer(State s) {
            return ROOT.equals(s) ? -1 : game.currentPlayer(s);
        }

        public List<String> legalActions(State s) {
            return game.legalActions(s);
        }

        public String informationSet(State s) {
            return game.informationSet(s);
        }

        public State afterAction(State s, String a) {
            return game.afterAction(s, a);
        }

        public List<ChanceOutcome<State>> chanceOutcomes(State s) {
            if (!ROOT.equals(s)) throw new IllegalArgumentException("Forced quality root required");
            return List.copyOf(roots);
        }
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static Report readReport(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Report.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded flop feedback exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored flop feedback exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path request, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(request.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var saved = readReport(report);
        var input = readRequest(request);
        if (!input.equals(saved.request()))
            throw new IllegalArgumentException("Flop feedback request differs");
        var owned = solve(input);
        if (!owned.report().equals(saved))
            throw new IllegalArgumentException("Physical flop feedback replay differs");
        return owned;
    }
}
