package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Separately declared root outer bounds; neither exact interval witnesses nor grading authority.
 */
public final class SixMaxRootEnvelopeStudy {
    public static final String REQUEST_SCHEMA = "pokerlab-root-envelope-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-root-envelope-report/v1";
    public static final String STATUS = "ROOT_SECURITY_ENVELOPE_RESEARCH_ONLY";
    public static final String REACH_UNITS = "ORIGINAL_JOINT_ROOT_CHANCE/v1";

    public record Request(
            String schemaVersion,
            SixMaxSuppliedFlopSizeMenuStudy.Request parent,
            int hero,
            String heroCards) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion)
                    || parent == null
                    || hero != 5
                    || !"9d 9h".equals(heroCards))
                throw new IllegalArgumentException("Declared BB99 envelope required");
            SixMaxReducedPreflopStudy.rootQuestion(parent, hero, heroCards);
        }
    }

    public record Failure(String kind, String reason, String detail) {
        public Failure {
            if (!Set.of("LP", "PARENT", "ENVELOPE").contains(kind)
                    || reason == null
                    || detail == null)
                throw new IllegalArgumentException("Typed envelope failure required");
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            String evScope,
            String reachUnits,
            SixMaxSizeMenuPreflopPlan.Table context,
            SixMaxSuppliedFlopSizeMenuStudy.Report parent,
            FiniteTwoPlayerRootSecurityEnvelope.Audit envelope,
            Failure rejection,
            boolean completeOuterBounds) {
        public Report {
            if (!REPORT_SCHEMA.equals(schemaVersion)
                    || !STATUS.equals(publicationStatus)
                    || trainerAdmission
                    || request == null
                    || parent == null
                    || !parent.request().equals(request.parent())
                    || !SixMaxSuppliedFlopFeedback.EV_SCOPE.equals(evScope)
                    || !REACH_UNITS.equals(reachUnits)
                    || (envelope == null) == (rejection == null)
                    || completeOuterBounds != (envelope != null))
                throw new IllegalArgumentException("Envelope research report identity differs");
            var q =
                    SixMaxReducedPreflopStudy.rootQuestion(
                            request.parent(), request.hero(), request.heroCards());
            if (!q.context().equals(context))
                throw new IllegalArgumentException("Original preflop root context required");
            if (envelope != null
                    && (!FiniteTwoPlayerRootSecurityEnvelope.ALGORITHM.equals(envelope.algorithm())
                            || !FiniteTwoPlayerRootSecurityEnvelope.SCOPE.equals(envelope.scope())
                            || envelope.trainerAdmission()
                            || !FiniteTwoPlayerRootSecurityEnvelope.POLICY.equals(
                                    envelope.fixedPolicyAlgorithm())
                            || !envelope.baseline().equals(parent.solve())
                            || !envelope.question()
                                    .equals(
                                            new FiniteTwoPlayerRootSecurityEnvelope.Question(
                                                    request.hero(), q.informationSet()))
                            || !envelope.actions().stream()
                                    .map(a -> a.action())
                                    .toList()
                                    .equals(q.actions().stream().map(a -> a.action()).toList())))
                throw new IllegalArgumentException(
                        "Owned conservative envelope provenance differs");
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

    private SixMaxRootEnvelopeStudy() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var p = request.parent();
        return solve(request, new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu()));
    }

    static Result solve(Request request, SixMaxSuppliedFlopSizeMenuGame game) throws Exception {
        var p = request.parent();
        if (!p.input().equals(game.source().input())
                || !p.flop().equals(game.binding().physicalCarrier().flop())
                || !p.menu().equals(game.binding().menu()))
            throw new IllegalArgumentException("Owned envelope game differs");
        var q = SixMaxReducedPreflopStudy.rootQuestion(p, request.hero(), request.heroCards());
        var parent = SixMaxSuppliedFlopSizeMenuStudy.solve(p, game).report();
        FiniteTwoPlayerRootSecurityEnvelope.Audit envelope = null;
        Failure failure = null;
        try {
            envelope =
                    FiniteTwoPlayerRootSecurityEnvelope.solve(
                                    game,
                                    new FiniteTwoPlayerRootSecurityEnvelope.Question(
                                            request.hero(), q.informationSet()))
                            .audit();
        } catch (BoundedLinearProgram.Rejected f) {
            failure = new Failure("LP", f.reason().name(), f.getMessage());
        } catch (FiniteTwoPlayerAffineSequenceForm.Rejected f) {
            failure = new Failure("PARENT", f.reason().name(), f.getMessage());
        } catch (FiniteTwoPlayerRootSecurityEnvelope.Rejected f) {
            failure = new Failure("ENVELOPE", "NUMERICAL_VERIFICATION", f.getMessage());
        }
        return new Result(
                new Report(
                        REPORT_SCHEMA,
                        STATUS,
                        false,
                        request,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        REACH_UNITS,
                        q.context(),
                        parent,
                        envelope,
                        failure,
                        envelope != null));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(
                        SixMaxRankTexturePayoffTable.readBytes(
                                path, SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES),
                        Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES)
            throw new IllegalArgumentException("Expanded envelope byte cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var b = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(b)) {
                zip.write(bytes);
            }
            bytes = b.toByteArray();
        }
        if (bytes.length > SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES)
            throw new IllegalArgumentException("Stored envelope byte cap exceeded");
        Files.createDirectories(path.getParent());
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
    }

    public static Result replay(Path input, Path output) throws Exception {
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        var request = readRequest(input);
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(
                                        output, SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES),
                                Report.class);
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Envelope request differs");
        var owned = solve(request);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete envelope physical replay differs");
        return owned;
    }
}
