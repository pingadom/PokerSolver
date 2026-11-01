package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Owned complete-menu root qualification, separate from public admission and exact endpoints. */
public final class SixMaxRootEnvelopeFeedback {
    public static final String REQUEST_SCHEMA = "pokerlab-root-envelope-feedback-request/v1";
    public static final String REPORT_SCHEMA = "pokerlab-root-envelope-feedback-report/v1";
    public static final String STATUS = "ROOT_ENVELOPE_POLICY_QUALIFICATION_RESEARCH_ONLY";
    public static final int MAX_BYTES = SixMaxSuppliedFlopSizeMenuStudy.MAX_BYTES;

    public record Request(
            String schemaVersion, SixMaxRootEnvelopeStudy.Request envelope, double maximumLossBb) {
        public Request {
            if (!REQUEST_SCHEMA.equals(schemaVersion) || envelope == null)
                throw new IllegalArgumentException("Owned root envelope request required");
            SixMaxSuppliedIntervalFeedback.classify(0, 0, maximumLossBb);
        }
    }

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            Request request,
            SixMaxRootEnvelopeStudy.Report envelope,
            List<SixMaxSuppliedIntervalFeedback.Move> moves,
            RootEnvelopePolicyChecks.Check primary,
            List<RootEnvelopePolicyChecks.Check> references,
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
                    || envelope == null
                    || !request.envelope().equals(envelope.request())
                    || qualifiedForOfflineDrill != rejectionReasons.isEmpty())
                throw new IllegalArgumentException("Root qualification report identity differs");
            if (!envelope.completeOuterBounds()) {
                if (!moves.isEmpty()
                        || primary != null
                        || !references.isEmpty()
                        || !rejectionReasons.equals(List.of("INCOMPLETE_ROOT_ENVELOPE")))
                    throw new IllegalArgumentException("Incomplete source cannot be qualified");
            } else if (primary == null
                    || !moves.equals(
                            RootEnvelopePolicyChecks.moves(
                                    envelope.envelope(), request.maximumLossBb()))
                    || primary.iterations() != envelope.parent().candidate().iterations()
                    || !primary.solutionHash().equals(envelope.parent().solutionHash())
                    || !references.stream()
                            .map(c -> c.iterations())
                            .toList()
                            .equals(List.of(80000, 160000))
                    || !references.stream()
                            .map(c -> c.solutionHash())
                            .toList()
                            .equals(
                                    envelope.parent().references().stream()
                                            .map(r -> r.policyHash())
                                            .toList())
                    || !rejectionReasons.equals(
                            RootEnvelopePolicyChecks.reasons(moves, primary, references)))
                throw new IllegalArgumentException(
                        "Owned source policies, bounds and qualification reasons required");
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

    private SixMaxRootEnvelopeFeedback() {}

    public static Result solve(Request request) throws Exception {
        Objects.requireNonNull(request);
        var p = request.envelope().parent();
        var game = new SixMaxSuppliedFlopSizeMenuGame(p.input(), p.flop(), p.menu());
        return solve(request, game);
    }

    static Result solve(Request request, SixMaxSuppliedFlopSizeMenuGame game) throws Exception {
        return qualify(request, game, SixMaxRootEnvelopeStudy.solve(request.envelope(), game));
    }

    static Result qualify(
            Request request,
            SixMaxSuppliedFlopSizeMenuGame game,
            SixMaxRootEnvelopeStudy.Result owned)
            throws Exception {
        Objects.requireNonNull(owned);
        var source = owned.report();
        if (!request.envelope().equals(source.request())
                || !game.binding().equals(source.parent().binding()))
            throw new IllegalArgumentException("Owned root envelope qualification lineage differs");
        if (!source.completeOuterBounds())
            return new Result(
                    new Report(
                            REPORT_SCHEMA,
                            STATUS,
                            false,
                            request,
                            source,
                            List.of(),
                            null,
                            List.of(),
                            false,
                            List.of("INCOMPLETE_ROOT_ENVELOPE")));
        var checked = FiniteTwoPlayerSequenceForm.checked(game);
        var moves = RootEnvelopePolicyChecks.moves(source.envelope(), request.maximumLossBb());
        var primary =
                RootEnvelopePolicyChecks.assess(
                        checked,
                        source.envelope(),
                        source.parent().candidate(),
                        request.maximumLossBb());
        var references = new ArrayList<RootEnvelopePolicyChecks.Check>();
        for (var reference : source.parent().references())
            references.add(
                    RootEnvelopePolicyChecks.assess(
                            checked,
                            source.envelope(),
                            reference.policy(),
                            request.maximumLossBb()));
        var reasons = RootEnvelopePolicyChecks.reasons(moves, primary, references);
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
                        reasons.isEmpty(),
                        reasons));
    }

    public static Request readRequest(Path path) throws Exception {
        return SixMaxTexturePayoffTable.mapper()
                .readValue(SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES), Request.class);
    }

    public static void write(Path path, Result result) throws Exception {
        path = path.toAbsolutePath().normalize();
        byte[] bytes = SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Expanded root feedback byte cap exceeded");
        if (path.toString().endsWith(".gz")) {
            var buffer = new java.io.ByteArrayOutputStream();
            try (var zip = new java.util.zip.GZIPOutputStream(buffer)) {
                zip.write(bytes);
            }
            bytes = buffer.toByteArray();
        }
        if (bytes.length > MAX_BYTES)
            throw new IllegalArgumentException("Stored root feedback byte cap exceeded");
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
        if (!request.equals(saved.request()))
            throw new IllegalArgumentException("Root feedback request differs");
        var owned = solve(request);
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete root feedback physical replay differs");
        return owned;
    }
}
