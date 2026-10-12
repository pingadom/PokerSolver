package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Physical replay and full-parent plus reached-flop quality for the bounded connected model. */
public final class SixMaxSuppliedFlopBettingStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-supplied-connected-flop-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-supplied-connected-flop-report/v1";
    public static final String STATUS = "CONNECTED_SINGLE_FLOP_RESEARCH_ONLY";
    public static final String LOCAL_SCOPE =
            "CANDIDATE_POLICY_REACHED_FLOP_POSTERIOR_NOT_FULL_SECURITY_FACE/v1";
    public static final int MAX_BYTES = 8 * 1024 * 1024;
    public static final double MIN_CONDITIONAL_BOUNDARY_REACH = .0001;

    public enum Conditioning {
        ABOVE_CANDIDATE_REACH_FLOOR,
        BELOW_CANDIDATE_REACH_FLOOR
    }

    public record WorldMass(int originalDealIndex, double jointRootFlopReach) {
        public WorldMass {
            if (originalDealIndex < 0
                    || !Double.isFinite(jointRootFlopReach)
                    || jointRootFlopReach <= 0
                    || jointRootFlopReach > 1)
                throw new IllegalArgumentException("Invalid raw candidate world mass");
        }
    }

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            List<String> flop,
            double betPotFraction) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion) || input == null)
                throw new IllegalArgumentException("Unsupported connected flop request");
            flop = ExactDeadCardFlopShowdown.canonicalFlop(flop);
            SixMaxSuppliedFlopBettingGame.preflight(input, flop, betPotFraction);
        }
    }

    public record ReachedWorld(
            int originalDealIndex, double jointRootFlopReach, double conditionalWeight) {
        public ReachedWorld {
            if (originalDealIndex < 0
                    || !Double.isFinite(jointRootFlopReach)
                    || jointRootFlopReach <= 0
                    || jointRootFlopReach > 1
                    || !Double.isFinite(conditionalWeight)
                    || conditionalWeight <= 0
                    || conditionalWeight > 1)
                throw new IllegalArgumentException("Invalid candidate reached world");
        }
    }

    public record LocalAudit(
            String scope,
            String publicHistory,
            double candidateRootFlopReach,
            double physicalSupportFlopMass,
            double candidateBoundaryReachGivenPhysicalFlop,
            double conditionalReachFloor,
            Conditioning conditioning,
            List<WorldMass> rawWorldMasses,
            List<ReachedWorld> reachedWorlds,
            MultiPlayerInformationSetBestResponse.Report conditionalBestResponse) {
        public LocalAudit {
            reachedWorlds = List.copyOf(reachedWorlds);
            rawWorldMasses = List.copyOf(rawWorldMasses);
            boolean supported =
                    candidateBoundaryReachGivenPhysicalFlop >= MIN_CONDITIONAL_BOUNDARY_REACH;
            if (!LOCAL_SCOPE.equals(scope)
                    || publicHistory == null
                    || !Double.isFinite(candidateRootFlopReach)
                    || candidateRootFlopReach < 0
                    || candidateRootFlopReach > 1
                    || !Double.isFinite(physicalSupportFlopMass)
                    || physicalSupportFlopMass <= 0
                    || !Double.isFinite(candidateBoundaryReachGivenPhysicalFlop)
                    || candidateBoundaryReachGivenPhysicalFlop < 0
                    || candidateBoundaryReachGivenPhysicalFlop > 1 + 1e-12
                    || candidateBoundaryReachGivenPhysicalFlop
                            != candidateRootFlopReach / physicalSupportFlopMass
                    || conditionalReachFloor != MIN_CONDITIONAL_BOUNDARY_REACH
                    || conditioning
                            != (supported
                                    ? Conditioning.ABOVE_CANDIDATE_REACH_FLOOR
                                    : Conditioning.BELOW_CANDIDATE_REACH_FLOOR)
                    || (candidateRootFlopReach == 0) != rawWorldMasses.isEmpty()
                    || supported == reachedWorlds.isEmpty()
                    || supported == (conditionalBestResponse == null))
                throw new IllegalArgumentException("Invalid reached-flop quality scope");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            SixMaxSuppliedFlopBettingGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> originalJointPrior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> sourcePayoffs,
            List<SixMaxSuppliedFlopHandoff.Audit> handoffs,
            List<SixMaxSuppliedFlopBettingGame.Residual> residuals,
            String solutionHash,
            CfrSolution candidate,
            FiniteTwoPlayerAffineSequenceForm.Audit solve,
            List<LocalAudit> reachedFlopQuality) {
        public Report {
            originalJointPrior = List.copyOf(originalJointPrior);
            sourcePayoffs = List.copyOf(sourcePayoffs);
            handoffs = List.copyOf(handoffs);
            residuals = List.copyOf(residuals);
            reachedFlopQuality = List.copyOf(reachedFlopQuality);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || candidate == null
                    || solve == null
                    || !FiniteTwoPlayerAffineSequenceForm.ASCENDING_ROWS_ALGORITHM.equals(
                            solve.algorithm())
                    || !BoundedLinearProgram.ASCENDING_L1_ALGORITHM.equals(
                            solve.firstLp().algorithm())
                    || !BoundedLinearProgram.ASCENDING_L1_ALGORITHM.equals(
                            solve.secondLp().algorithm())
                    || solutionHash == null
                    || !solutionHash.matches("[0-9a-f]{64}")
                    || !solutionHash.equals(solve.behavioralPolicyHash())
                    || candidate.iterations() != 1
                    || originalJointPrior.size() != binding.budget().sourceBudget().jointDeals()
                    || sourcePayoffs.size() != originalJointPrior.size()
                    || handoffs.size() != binding.budget().nonAllInBoundaries()
                    || reachedFlopQuality.size() != handoffs.size()
                    || !request.flop().equals(binding.flop())
                    || request.betPotFraction() != binding.betPotFraction())
                throw new IllegalArgumentException("Invalid connected flop report identity");
        }
    }

    public static final class Result {
        private final Report report;

        private Result(Report report) {
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxSuppliedFlopBettingStudy() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        return solve(
                request,
                new SixMaxSuppliedFlopBettingGame(
                        request.input(), request.flop(), request.betPotFraction()));
    }

    static Result solve(Request request, SixMaxSuppliedFlopBettingGame game) throws Exception {
        if (!request.input().equals(game.source().input())
                || !request.flop().equals(game.binding().flop())
                || request.betPotFraction() != game.binding().betPotFraction())
            throw new IllegalArgumentException("Connected game lineage differs");
        // Chosen before optimization; no retry, scaling, coefficient deletion or changed tolerance.
        var solved = FiniteTwoPlayerAffineSequenceForm.solveAscendingL1Rows(game);
        var policy = new CfrSolution(1, solved.strategy());
        var local = new ArrayList<LocalAudit>();
        for (var handoff : game.handoffs()) local.add(localAudit(game, handoff, policy));
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        game.binding(),
                        game.source().prior(),
                        game.source().payoffs(),
                        game.handoffs(),
                        game.residuals(),
                        solved.audit().behavioralPolicyHash(),
                        policy,
                        solved.audit(),
                        local));
    }

    static LocalAudit localAudit(
            SixMaxSuppliedFlopBettingGame game,
            SixMaxSuppliedFlopHandoff.Audit handoff,
            CfrSolution policy) {
        var source = game.source();
        var states = new ArrayList<SixMaxSuppliedFlopBettingGame.State>();
        var masses = new ArrayList<Double>();
        for (var world : handoff.worlds()) {
            var state = new SixMaxHeadsUpPreflopGame.State(world.originalDealIndex(), "");
            double weight =
                    world.originalJointProbability() * world.physicalFlopProbabilityGivenDeal();
            for (String action : handoff.continuationActions()) {
                int actor = source.currentPlayer(state);
                var row =
                        policy.at(
                                actor,
                                "supplied-connected-single-flop:" + source.informationSet(state));
                if (row == null || !row.containsKey(action))
                    throw new IllegalArgumentException("Missing candidate preflop action");
                double p = row.get(action), next = weight * p;
                if (weight > 0 && p > 0 && next == 0)
                    throw new IllegalArgumentException("Positive candidate reach underflow");
                weight = next;
                state = source.afterAction(state, action);
            }
            if (weight > 0) {
                states.add(
                        new SixMaxSuppliedFlopBettingGame.State(
                                world.originalDealIndex(),
                                handoff.publicHistory(),
                                SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                                ""));
                masses.add(weight);
            }
        }
        double total = masses.stream().mapToDouble(Double::doubleValue).sum();
        var raw = new ArrayList<WorldMass>();
        for (int i = 0; i < states.size(); i++)
            raw.add(new WorldMass(states.get(i).originalDealIndex(), masses.get(i)));
        double conditionalBoundaryReach = total / handoff.physicalFlopMass();
        if (conditionalBoundaryReach < MIN_CONDITIONAL_BOUNDARY_REACH)
            return new LocalAudit(
                    LOCAL_SCOPE,
                    handoff.publicHistory(),
                    total,
                    handoff.physicalFlopMass(),
                    conditionalBoundaryReach,
                    MIN_CONDITIONAL_BOUNDARY_REACH,
                    Conditioning.BELOW_CANDIDATE_REACH_FLOOR,
                    raw,
                    List.of(),
                    null);
        var reached = new ArrayList<ReachedWorld>();
        var roots = new ArrayList<ChanceOutcome<SixMaxSuppliedFlopBettingGame.State>>();
        for (int i = 0; i < states.size(); i++) {
            double probability = masses.get(i) / total;
            reached.add(
                    new ReachedWorld(
                            states.get(i).originalDealIndex(), masses.get(i), probability));
            roots.add(new ChanceOutcome<>(states.get(i), probability));
        }
        // This artificial root is confined to quality assessment, never the connected parent.
        var root =
                new SixMaxSuppliedFlopBettingGame.State(
                        -2,
                        handoff.publicHistory(),
                        SixMaxSuppliedFlopBettingGame.Phase.FLOP,
                        "local-quality-root");
        MultiPlayerCfrGame<SixMaxSuppliedFlopBettingGame.State> local =
                new MultiPlayerCfrGame<>() {
                    public int playerCount() {
                        return 6;
                    }

                    public SixMaxSuppliedFlopBettingGame.State initialState() {
                        return root;
                    }

                    public boolean isTerminal(SixMaxSuppliedFlopBettingGame.State s) {
                        return !s.equals(root) && game.isTerminal(s);
                    }

                    public double[] terminalUtilities(SixMaxSuppliedFlopBettingGame.State s) {
                        return game.terminalUtilities(s);
                    }

                    public int currentPlayer(SixMaxSuppliedFlopBettingGame.State s) {
                        return s.equals(root) ? -1 : game.currentPlayer(s);
                    }

                    public List<String> legalActions(SixMaxSuppliedFlopBettingGame.State s) {
                        return game.legalActions(s);
                    }

                    public String informationSet(SixMaxSuppliedFlopBettingGame.State s) {
                        return game.informationSet(s);
                    }

                    public SixMaxSuppliedFlopBettingGame.State afterAction(
                            SixMaxSuppliedFlopBettingGame.State s, String a) {
                        return game.afterAction(s, a);
                    }

                    public List<ChanceOutcome<SixMaxSuppliedFlopBettingGame.State>> chanceOutcomes(
                            SixMaxSuppliedFlopBettingGame.State s) {
                        if (!s.equals(root))
                            throw new IllegalArgumentException("Local quality root required");
                        return List.copyOf(roots);
                    }
                };
        return new LocalAudit(
                LOCAL_SCOPE,
                handoff.publicHistory(),
                total,
                handoff.physicalFlopMass(),
                conditionalBoundaryReach,
                MIN_CONDITIONAL_BOUNDARY_REACH,
                Conditioning.ABOVE_CANDIDATE_REACH_FLOOR,
                raw,
                reached,
                MultiPlayerInformationSetBestResponse.assess(local, policy));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Connected report exceeds expanded byte cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Connected report exceeds stored byte cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path requestPath, Path reportPath) throws Exception {
        requestPath = requestPath.toAbsolutePath().normalize();
        reportPath = reportPath.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath));
        var request = readRequest(requestPath);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(reportPath, MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request())
                || !SixMaxHeadsUpPreflopGame.hash(request.input())
                        .equals(saved.binding().sourceBinding().inputHash())
                || !SixMaxHeadsUpPreflopGame.hash(saved.handoffs())
                        .equals(saved.binding().handoffsHash())
                || !SixMaxHeadsUpPreflopGame.hash(saved.residuals())
                        .equals(saved.binding().residualsHash())
                || !SixMaxConnectedPostflopAudit.solutionHash(saved.candidate())
                        .equals(saved.solutionHash()))
            throw new IllegalArgumentException("Connected report lineage differs");
        var expected = solve(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Physical connected flop replay differs");
        return expected;
    }
}
