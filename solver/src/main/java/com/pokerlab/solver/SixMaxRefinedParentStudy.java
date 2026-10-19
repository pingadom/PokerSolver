package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Explicit refined parent study; no interval grading, reference training or trainer admission. */
public final class SixMaxRefinedParentStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-refined-parent-flop-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-refined-parent-flop-report/v1";
    public static final String STATUS = "REFINED_SINGLE_FLOP_PARENT_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedFlopBettingStudy.MAX_BYTES;

    public record Request(
            String schemaVersion,
            String algorithm,
            SixMaxSuppliedFlopBettingStudy.Request connected) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || !FiniteTwoPlayerAffineSequenceForm.ASCENDING_REFINED_DIRECTIONS_ALGORITHM
                            .equals(algorithm)
                    || connected == null)
                throw new IllegalArgumentException("Explicit refined parent request required");
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
            FiniteTwoPlayerAffineSequenceForm.LpWitness firstLpWitness,
            FiniteTwoPlayerAffineSequenceForm.LpWitness secondLpWitness) {
        public Report {
            originalJointPrior = List.copyOf(originalJointPrior);
            sourcePayoffs = List.copyOf(sourcePayoffs);
            handoffs = List.copyOf(handoffs);
            residuals = List.copyOf(residuals);
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || binding == null
                    || candidate == null
                    || solve == null
                    || !request.algorithm().equals(solve.algorithm())
                    || !BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM.equals(
                            solve.firstLp().algorithm())
                    || !BoundedLinearProgram.ASCENDING_REFINED_DIRECTION_ALGORITHM.equals(
                            solve.secondLp().algorithm())
                    || firstLpWitness == null
                    || secondLpWitness == null
                    || !firstLpWitness.certificate().equals(solve.firstLp())
                    || !secondLpWitness.certificate().equals(solve.secondLp())
                    || candidate.iterations() != 1
                    || solutionHash == null
                    || !solutionHash.matches("[0-9a-f]{64}")
                    || !Objects.equals(solutionHash, solve.behavioralPolicyHash())
                    || !request.connected().flop().equals(binding.flop())
                    || request.connected().betPotFraction() != binding.betPotFraction()
                    || originalJointPrior.size() != binding.budget().sourceBudget().jointDeals()
                    || sourcePayoffs.size() != originalJointPrior.size()
                    || handoffs.size() != binding.budget().nonAllInBoundaries()
                    || residuals.size()
                            != binding.budget().nonAllInBoundaries()
                                    * binding.budget().compatibleDeals())
                throw new IllegalArgumentException("Invalid refined parent report");
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

    private SixMaxRefinedParentStudy() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var input = request.connected();
        var game =
                new SixMaxSuppliedFlopBettingGame(
                        input.input(), input.flop(), input.betPotFraction());
        var solved = FiniteTwoPlayerAffineSequenceForm.solveAscendingRefinedDirections(game);
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
                        new CfrSolution(1, solved.strategy()),
                        solved.audit(),
                        solved.firstLpWitness(),
                        solved.secondLpWitness()));
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
        byte[] bytes =
                SixMaxTextureStudy.json(Objects.requireNonNull(result).report())
                        .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded refined parent exceeds cap");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored refined parent exceeds cap");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path report) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(
                List.of(input.toAbsolutePath().normalize(), report.toAbsolutePath().normalize()));
        var request = readRequest(input);
        var saved = readReport(report);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Refined parent request differs");
        var owned = solve(request);
        if (!owned.report().equals(saved))
            throw new IllegalArgumentException("Complete refined parent replay differs");
        return owned;
    }
}
