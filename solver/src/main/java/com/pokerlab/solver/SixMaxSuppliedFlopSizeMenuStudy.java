package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Owned solve and two fresh references; serialized evidence never grants trainer admission. */
public final class SixMaxSuppliedFlopSizeMenuStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-supplied-flop-size-menu-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-supplied-flop-size-menu-report/v1";
    public static final String STATUS = "JOINTLY_SOLVED_SIZE_MENU_RESEARCH_ONLY";
    public static final int MAX_BYTES = 8 * 1024 * 1024;

    public record Request(
            String schemaVersion,
            SixMaxSuppliedRangePreflopGame.Input input,
            List<String> flop,
            SixMaxSuppliedFlopSizeMenuGame.Menu menu) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion) || input == null || menu == null)
                throw new IllegalArgumentException("Invalid size-menu request");
            flop = ExactDeadCardFlopShowdown.canonicalFlop(flop);
            SixMaxSuppliedFlopSizeMenuGame.preflight(input, flop, menu);
        }
    }

    public record Reference(
            CfrSolution policy,
            String policyHash,
            int validatedTreeNodes,
            Map<Integer, Double> inactiveUtilities,
            MultiPlayerCfrSolver.Statistics statistics,
            long prunedSuffixRoots,
            MultiPlayerInformationSetBestResponse.Report quality) {
        public Reference {
            inactiveUtilities = Map.copyOf(inactiveUtilities);
            if (policy == null
                    || !SixMaxConnectedPostflopAudit.solutionHash(policy).equals(policyHash)
                    || (policy.iterations() != 80_000 && policy.iterations() != 160_000)
                    || validatedTreeNodes < 1
                    || inactiveUtilities.size() != 4
                    || statistics == null
                    || prunedSuffixRoots < 0
                    || quality == null)
                throw new IllegalArgumentException("Invalid fresh size-menu reference");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            SixMaxSuppliedFlopSizeMenuGame.Binding binding,
            List<SixMaxSuppliedRangePreflopGame.JointDeal> originalJointPrior,
            List<SixMaxSuppliedRangePreflopGame.Payoff> sourcePayoffs,
            List<SixMaxSuppliedFlopHandoff.Audit> handoffs,
            List<SixMaxSuppliedFlopBettingGame.Residual> residuals,
            String solutionHash,
            CfrSolution candidate,
            FiniteTwoPlayerAffineSequenceForm.Audit solve,
            FiniteTwoPlayerAffineSequenceForm.LpWitness firstLpWitness,
            FiniteTwoPlayerAffineSequenceForm.LpWitness secondLpWitness,
            List<Reference> references) {
        public Report {
            originalJointPrior = List.copyOf(originalJointPrior);
            sourcePayoffs = List.copyOf(sourcePayoffs);
            handoffs = List.copyOf(handoffs);
            residuals = List.copyOf(residuals);
            references = List.copyOf(references);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || candidate == null
                    || solve == null
                    || firstLpWitness == null
                    || secondLpWitness == null
                    || !request.menu().equals(binding.menu())
                    || !request.flop().equals(binding.physicalCarrier().flop())
                    || !hash(request.input())
                            .equals(binding.physicalCarrier().sourceBinding().inputHash())
                    || !hash(handoffs).equals(binding.physicalCarrier().handoffsHash())
                    || !hash(residuals).equals(binding.physicalCarrier().residualsHash())
                    || !SixMaxConnectedPostflopAudit.solutionHash(candidate).equals(solutionHash)
                    || !solutionHash.equals(solve.behavioralPolicyHash())
                    || candidate.iterations() != 1
                    || !FiniteTwoPlayerAffineSequenceForm.ASCENDING_REFINED_DIRECTIONS_ALGORITHM
                            .equals(solve.algorithm())
                    || solve.treeNodes() != binding.budget().completeTreeStates()
                    || originalJointPrior.size() != binding.budget().sourceBudget().jointDeals()
                    || sourcePayoffs.size() != originalJointPrior.size()
                    || handoffs.size() != binding.budget().nonAllInBoundaries()
                    || !firstLpWitness.certificate().equals(solve.firstLp())
                    || !secondLpWitness.certificate().equals(solve.secondLp())
                    || references.size() != 2
                    || references.get(0).policy().iterations() != 80_000
                    || references.get(1).policy().iterations() != 160_000
                    || references.stream()
                            .anyMatch(r -> r.validatedTreeNodes() != solve.treeNodes()))
                throw new IllegalArgumentException("Invalid size-menu report identity");
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

    private static String hash(Object value) {
        try {
            return SixMaxHeadsUpPreflopGame.hash(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid size-menu lineage", exception);
        }
    }

    private SixMaxSuppliedFlopSizeMenuStudy() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        return solve(
                request,
                new SixMaxSuppliedFlopSizeMenuGame(
                        request.input(), request.flop(), request.menu()));
    }

    static Result solve(Request request, SixMaxSuppliedFlopSizeMenuGame game) throws Exception {
        if (!request.input().equals(game.source().input())
                || !request.menu().equals(game.binding().menu())
                || !request.flop().equals(game.binding().physicalCarrier().flop()))
            throw new IllegalArgumentException("Size-menu game lineage differs");
        // Declared once before optimization: no fallback, retry, coefficient deletion or gate
        // change.
        var solved = FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(game);
        var policy = new CfrSolution(1, solved.strategy());
        var traversal = new CachedTwoPlayerCfrGame(game);
        var references = new ArrayList<Reference>();
        for (int iterations : List.of(80_000, 160_000)) {
            var solver =
                    new MultiPlayerCfrSolver<>(
                            traversal,
                            CfrSolver.Variant.CFR_PLUS,
                            MultiPlayerCfrSolver.InactivePruning.FIXED_UTILITY);
            var reference = solver.solve(iterations);
            references.add(
                    new Reference(
                            reference,
                            SixMaxConnectedPostflopAudit.solutionHash(reference),
                            traversal.validatedTreeNodes(),
                            traversal.inactiveUtilities(),
                            solver.statistics(),
                            solver.inactiveUtilityPrunedNodes(),
                            MultiPlayerInformationSetBestResponse.assess(game, reference)));
        }
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
                        solved.firstLpWitness(),
                        solved.secondLpWitness(),
                        references));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded report cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored report cap exceeded");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path requestPath, Path reportPath) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath));
        var request = readRequest(requestPath);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(reportPath, MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Saved request differs");
        var expected = solve(request);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Physical size-menu replay differs");
        return expected;
    }
}
