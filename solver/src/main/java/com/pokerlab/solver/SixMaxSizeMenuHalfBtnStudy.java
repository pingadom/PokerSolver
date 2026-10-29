package com.pokerlab.solver;

import static com.pokerlab.solver.SixMaxSizeMenuBtnStudy.actions;
import static com.pokerlab.solver.SixMaxSizeMenuBtnStudy.hands;
import static com.pokerlab.solver.SixMaxSizeMenuBtnStudy.moves;
import static com.pokerlab.solver.SixMaxSizeMenuBtnStudy.reasons;

import com.pokerlab.solver.SixMaxSizeMenuBtnStudy.Endpoint;
import com.pokerlab.solver.SixMaxSizeMenuBtnStudy.Rejection;
import com.pokerlab.solver.SixMaxSizeMenuBtnStudy.Utility;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.Phase;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame.State;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback.Check;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete four-hand half-pot response study; reuses the original endpoint records and gates. */
public final class SixMaxSizeMenuHalfBtnStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-size-menu-btn-half-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-size-menu-btn-half-report/v1";
    public static final String STATUS = "SIZE_MENU_BTN_HALF_RESEARCH_ONLY";
    public static final String HISTORY = "|BB:call";
    public static final String BET = "bet:0.5";
    public static final int MAX_BYTES = SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopSizeMenuStudy.Request parent,
            double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || maximumLossBb != .01
                    || !parent.menu().firstToAct().contains(.5)
                    || hands(parent).size() != 4)
                throw new IllegalArgumentException(
                        "Complete four-hand BTN half-pot study required");
        }
    }

    public record Case(
            int index,
            String heroCards,
            String informationSet,
            SixMaxSuppliedFlopSizeMenuGame.PublicState context,
            List<Endpoint> endpoints,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            Check primary,
            List<Check> references,
            boolean qualifiedForOfflineDrill,
            List<String> rejectionReasons) {
        public Case {
            endpoints = List.copyOf(endpoints);
            moves = List.copyOf(moves);
            references = List.copyOf(references);
            rejectionReasons = List.copyOf(rejectionReasons);
            boolean complete = endpoints.stream().allMatch(e -> e.utility() != null);
            if (index < 0
                    || index >= 4
                    || heroCards == null
                    || informationSet == null
                    || context == null
                    || context.actingSeat().ordinal() != 3
                    || !context.publicPreflopHistory().equals(HISTORY)
                    || !context.flopHistory().stream()
                            .map(m -> m.action())
                            .toList()
                            .equals(List.of(BET))
                    || !endpoints.stream()
                            .map(Endpoint::action)
                            .toList()
                            .equals(List.of("fold", "call"))
                    || complete != (primary != null)
                    || (complete ? references.size() != 2 : !references.isEmpty())
                    || (complete
                            ? !moves.equals(SixMaxSizeMenuBtnStudy.moves(endpoints, .01))
                            : !moves.isEmpty())
                    || !rejectionReasons.equals(reasons(endpoints, moves, primary, references))
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Invalid complete BTN case");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            List<Case> cases,
            List<Integer> qualifiedIndices) {
        public Report {
            cases = List.copyOf(cases);
            qualifiedIndices = List.copyOf(qualifiedIndices);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || parent == null
                    || !request.parent().equals(parent.request())
                    || cases.size() != 4
                    || !cases.stream().map(Case::index).toList().equals(List.of(0, 1, 2, 3))
                    || !cases.stream().map(Case::heroCards).toList().equals(hands(request.parent()))
                    || !qualifiedIndices.equals(
                            cases.stream()
                                    .filter(Case::qualifiedForOfflineDrill)
                                    .map(Case::index)
                                    .toList()))
                throw new IllegalArgumentException("Invalid complete BTN study");
            for (var item : cases) {
                for (var endpoint : item.endpoints())
                    if (endpoint.utility() != null) {
                        var audit = endpoint.utility().diagnostic();
                        if (audit == null
                                || audit.trainerAdmission()
                                || !audit.baseline().equals(parent.solve())
                                || !audit.algorithm()
                                        .equals(
                                                FiniteTwoPlayerConditionalActionIntervals
                                                        .PHYSICAL_FLOP_PHASE_ONE_REFINED_ALGORITHM)
                                || !audit.question()
                                        .equals(
                                                new FiniteTwoPlayerConditionalActionIntervals
                                                        .Question(
                                                        3,
                                                        item.informationSet(),
                                                        endpoint.action()))
                                || audit.securitySlack()
                                        != SixMaxSuppliedFlopActionBounds.SECURITY_SLACK
                                || audit.requiredMinimumReach()
                                        != SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR)
                            throw new IllegalArgumentException("BTN endpoint lineage differs");
                    }
                if (item.primary() != null
                        && (item.primary().iterations() != 1
                                || !item.primary().solutionHash().equals(parent.solutionHash())
                                || !item.references().stream()
                                        .map(Check::iterations)
                                        .toList()
                                        .equals(List.of(80_000, 160_000))
                                || !item.references().stream()
                                        .map(Check::solutionHash)
                                        .toList()
                                        .equals(
                                                parent.references().stream()
                                                        .map(r -> r.policyHash())
                                                        .toList())))
                    throw new IllegalArgumentException("BTN fresh-policy lineage differs");
            }
        }
    }

    public static final class QualifiedCase {
        private final String studyHash;
        private final int index;

        private QualifiedCase(String studyHash, int index) {
            this.studyHash = studyHash;
            this.index = index;
        }

        public String studyHash() {
            return studyHash;
        }

        public int index() {
            return index;
        }
    }

    public static final class Result {
        private final Report report;
        private final Map<Integer, QualifiedCase> qualified;

        private Result(Report report) throws Exception {
            this.report = report;
            String hash = SixMaxHeadsUpPreflopGame.hash(report);
            var caps = new TreeMap<Integer, QualifiedCase>();
            for (int i : report.qualifiedIndices()) caps.put(i, new QualifiedCase(hash, i));
            qualified = Collections.unmodifiableMap(caps);
        }

        public Report report() {
            return report;
        }

        public Map<Integer, QualifiedCase> qualifiedCases() {
            return qualified;
        }
    }

    record Question(String hand, String key, SixMaxSuppliedFlopSizeMenuGame.PublicState context) {}

    private SixMaxSizeMenuHalfBtnStudy() {}

    static Question question(SixMaxSuppliedFlopSizeMenuGame game, String hand) {
        Question result = null;
        var handoff =
                game.handoffs().stream()
                        .filter(h -> h.publicHistory().equals(HISTORY))
                        .findFirst()
                        .orElseThrow();
        for (var world : handoff.worlds())
            if (world.sixDealtCombos().get(3).equals(hand)) {
                var state =
                        game.afterAction(
                                new State(world.originalDealIndex(), HISTORY, Phase.FLOP, ""), BET);
                if (game.currentPlayer(state) != 3
                        || !game.legalActions(state).equals(List.of("fold", "call")))
                    throw new IllegalArgumentException("Owned BTN response required");
                var found =
                        new Question(
                                hand,
                                "3:" + game.informationSet(state),
                                game.publicFlopState(state));
                if (result != null && !result.equals(found))
                    throw new IllegalArgumentException("Hidden-world public context differs");
                result = found;
            }
        if (result == null) throw new IllegalArgumentException("No compatible BTN hand");
        return result;
    }

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var p = request.parent();
        return solve(request, new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu()));
    }

    static Result solve(Request request, SixMaxSuppliedFlopSizeMenuGame game) throws Exception {
        if (!request.parent().input().equals(game.source().input())
                || !request.parent().flop().equals(game.binding().physicalCarrier().flop())
                || !request.parent().menu().equals(game.binding().menu()))
            throw new IllegalArgumentException("BTN study game identity differs");
        var questions = hands(request.parent()).stream().map(h -> question(game, h)).toList();
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(request.parent(), game).report();
        var cases = new ArrayList<Case>();
        for (var q : questions) {
            var owned =
                    new LinkedHashMap<String, FiniteTwoPlayerConditionalActionIntervals.Result>();
            var rejected = new LinkedHashMap<String, Rejection>();
            // Each declared action gets exactly one attempt; failures do not select another mode.
            for (String action : List.of("fold", "call")) {
                try {
                    owned.put(
                            action,
                            FiniteTwoPlayerConditionalActionIntervals.solveSizeMenuPhysicalFlop(
                                    game,
                                    new FiniteTwoPlayerConditionalActionIntervals.Question(
                                            3, q.key(), action),
                                    SixMaxSuppliedFlopActionBounds.SECURITY_SLACK,
                                    SixMaxSuppliedFlopActionBounds.CONDITIONAL_REACH_FLOOR));
                } catch (FiniteTwoPlayerConditionalActionIntervals.Rejected failure) {
                    rejected.put(
                            action,
                            new Rejection(
                                    "CONDITIONAL",
                                    failure.reason().name(),
                                    failure.getMessage(),
                                    failure.reachFailure().orElse(null)));
                } catch (BoundedLinearProgram.Rejected failure) {
                    rejected.put(
                            action,
                            new Rejection(
                                    "LP", failure.reason().name(), failure.getMessage(), null));
                } catch (FiniteTwoPlayerAffineSequenceForm.Rejected failure) {
                    rejected.put(
                            action,
                            new Rejection(
                                    "PARENT", failure.reason().name(), failure.getMessage(), null));
                }
            }
            var endpoints = new ArrayList<Endpoint>();
            var lo = new TreeMap<String, Double>();
            var hi = new TreeMap<String, Double>();
            for (var entry : owned.entrySet()) {
                var result = entry.getValue();
                double shift = result.context().upperObjectiveShift();
                lo.put(
                        entry.getKey(),
                        -shift
                                - result.audit().lowerWitness().certificate().dualValue()
                                - BoundedLinearProgram.CERTIFICATE_TOLERANCE);
                hi.put(
                        entry.getKey(),
                        result.audit().upperWitnesses().stream()
                                        .mapToDouble(w -> w.certificate().dualValue() - shift)
                                        .max()
                                        .orElseThrow()
                                + BoundedLinearProgram.CERTIFICATE_TOLERANCE);
            }
            for (String action : List.of("fold", "call")) {
                var result = owned.get(action);
                if (result == null) endpoints.add(new Endpoint(action, null, rejected.get(action)));
                else {
                    var audit = result.audit();
                    double mass = game.handoffs().getFirst().physicalFlopMass();
                    endpoints.add(
                            new Endpoint(
                                    action,
                                    new Utility(
                                            lo.get(action),
                                            hi.get(action),
                                            result.context().upperObjectiveShift(),
                                            mass * audit.certifiedReachLowerBound(),
                                            mass * audit.certifiedReachUpperBound(),
                                            audit),
                                    null));
                }
            }
            var moves =
                    rejected.isEmpty()
                            ? moves(endpoints, request.maximumLossBb())
                            : List.<SixMaxSuppliedIntervalFeedback.Move>of();
            Check primary = null;
            List<Check> references = List.of();
            if (rejected.isEmpty()) {
                var selection =
                        new SizeMenuPolicyChecks.Selection(
                                3, q.hand(), HISTORY, List.of(BET), q.key());
                var actions = actions(endpoints);
                primary =
                        SizeMenuPolicyChecks.assess(
                                game, parent, selection, actions, parent.candidate());
                references =
                        parent.references().stream()
                                .map(
                                        r ->
                                                SizeMenuPolicyChecks.assess(
                                                        game,
                                                        parent,
                                                        selection,
                                                        actions,
                                                        r.policy()))
                                .toList();
            }
            var reasons = reasons(endpoints, moves, primary, references);
            cases.add(
                    new Case(
                            cases.size(),
                            q.hand(),
                            q.key(),
                            q.context(),
                            endpoints,
                            moves,
                            primary,
                            references,
                            reasons.isEmpty(),
                            reasons));
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        parent,
                        cases,
                        cases.stream()
                                .filter(Case::qualifiedForOfflineDrill)
                                .map(Case::index)
                                .toList()));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded BTN report cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored BTN report cap exceeded");
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
            throw new IllegalArgumentException("BTN request differs");
        var owned = solve(request);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete physical BTN replay differs");
        return owned;
    }
}
