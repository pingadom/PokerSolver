package com.pokerlab.solver;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Complete compatibility replay. Resource counters confer no equilibrium or trainer admission. */
public final class SixMaxHistoryPhysicalStorageAudit {
    public static final String SCHEMA = "six-max-history-physical-storage-audit/v1";
    public static final int MAX_BYTES = 128 * 1024;
    public static final int CONTROL_ITERATIONS = 8;

    public record Control(
            String history,
            int observation,
            String observationKey,
            String posteriorHash,
            String maxminAuditHash,
            String maxminStrategyHash,
            int freshCfrIterations,
            String freshCfrSolutionHash,
            MultiPlayerCfrSolver.Statistics freshCfrTraversal) {}

    public record Report(
            String schemaVersion,
            String publicationStatus,
            boolean trainerAdmission,
            SixMaxHistoryPhysicalStudy.Binding binding,
            String checkpointHash,
            String studyHash,
            String compactHash,
            SixMaxHistoryPhysicalCompactStorage.Layout layout,
            long comparedCounts,
            long comparedActiveShares,
            String diagnosticsHash,
            int informationSets,
            List<Control> localControls) {
        public Report {
            if (!SCHEMA.equals(schemaVersion)
                    || !"VALIDATION_ONLY".equals(publicationStatus)
                    || trainerAdmission
                    || comparedCounts < 1
                    || comparedActiveShares < 1
                    || informationSets < 1)
                throw new IllegalArgumentException("Invalid storage audit identity or counts");
            Objects.requireNonNull(binding, "binding");
            Objects.requireNonNull(layout, "layout");
            for (String hash : List.of(checkpointHash, studyHash, compactHash, diagnosticsHash))
                if (!hash.matches("[0-9a-f]{64}"))
                    throw new IllegalArgumentException("Invalid storage audit hash");
            localControls = List.copyOf(localControls);
            if (localControls.size() > 20) throw new IllegalArgumentException("Too many controls");
        }
    }

    /** Raw reports cannot grant an exportable compatibility result. */
    public static final class Result {
        private final Report report;

        private Result(Report report) {
            this.report = report;
        }

        public Report report() {
            return report;
        }
    }

    private SixMaxHistoryPhysicalStorageAudit() {}

    public static Result assess(
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified original,
            SixMaxHistoryPhysicalStudy.Validated predecessor,
            SixMaxHistoryPhysicalCompactStorage.Verified compact)
            throws Exception {
        Objects.requireNonNull(predecessor, "predecessor");
        var cp = predecessor.checkpoint();
        if (!cp.binding().equals(SixMaxHistoryPhysicalStudy.binding(original))
                || !compact.artifact().originalPayoffHash().equals(cp.binding().payoffTableHash())
                || !SixMaxHistoryPhysicalCompactStorage.restore(compact.artifact())
                        .equals(original.artifact()))
            throw new IllegalArgumentException("Storage audit predecessor differs");
        var before = predecessor.core();
        var after = compact.core(source, parent);
        var a = before.payoffView();
        var b = after.payoffView();
        require(a.namespace().equals(b.namespace()), "Public namespace differs");
        require(
                before.completeTreeStates() == after.completeTreeStates()
                        && before.coverage().equals(after.coverage()),
                "Complete game coverage differs");
        long counts = 0, shares = 0;
        for (int d = 0; d < a.dealCount(); d++)
            require(a.hands(d).equals(b.hands(d)), "Private world differs");
        for (var h : original.artifact().histories()) {
            for (int d = 0; d < h.deals().size(); d++) {
                var ac = a.counts(h.publicHistory(), d);
                var bc = b.counts(h.publicHistory(), d);
                require(ac.equals(bc), "Chance counts differ");
                counts += ac.size();
                for (int o = 0; o < ac.size(); o++) {
                    require(a.key(o).equals(b.key(o)), "Public observation order differs");
                    if (ac.get(o) == 0) continue;
                    for (int p = 0; p < 6; p++)
                        if ((h.activeMask() & (1 << p)) != 0) {
                            require(
                                    Double.doubleToLongBits(
                                                    a.share(
                                                            h.publicHistory(),
                                                            d,
                                                            h.activeMask(),
                                                            p,
                                                            o))
                                            == Double.doubleToLongBits(
                                                    b.share(
                                                            h.publicHistory(),
                                                            d,
                                                            h.activeMask(),
                                                            p,
                                                            o)),
                                    "Active share differs");
                            shares++;
                        }
                }
            }
        }
        // Includes complete support, every reached conditional branch and full parent responses.
        var diagnostics = SixMaxFlopConditionalDiagnostics.assess(after, cp.solution());
        require(
                diagnostics.equals(predecessor.report().jointlySolvedDiagnostics()),
                "Complete conditional/parent diagnostics differ");
        var controls = new ArrayList<Control>();
        var pre = SixMaxPreflopContinuationFeedback.preflopPolicy(cp.solution());
        for (var h : diagnostics.histories())
            for (var s : h.signals()) {
                if (controls.size() == 20
                        || s.quality() == null
                        || !s.observationKey().startsWith("board:")
                        || s.quality().nashConvBb() <= .001) continue;
                var transition =
                        new SixMaxPolicyFlopTransition(before.sourceGame(), pre, h.history());
                var pa =
                        SixMaxFlopConditionalDiagnostics.posterior(
                                before, transition, s.observation());
                var pb =
                        SixMaxFlopConditionalDiagnostics.posterior(
                                after, transition, s.observation());
                require(pa.equals(pb), "Hidden-world posterior differs");
                var ga = new SixMaxRankTextureConditionalAudit.ConditionalGame(before, pa.roots());
                var gb = new SixMaxRankTextureConditionalAudit.ConditionalGame(after, pb.roots());
                var ma = FiniteTwoPlayerMaxmin.solve(ga);
                var mb = FiniteTwoPlayerMaxmin.solve(gb);
                require(
                        ma.audit().equals(mb.audit()) && ma.strategy().equals(mb.strategy()),
                        "Owned maxmin result differs");
                var ca = new MultiPlayerCfrSolver<>(ga, CfrSolver.Variant.CFR_PLUS);
                var cb = new MultiPlayerCfrSolver<>(gb, CfrSolver.Variant.CFR_PLUS);
                var sa = ca.solve(CONTROL_ITERATIONS);
                var sb = cb.solve(CONTROL_ITERATIONS);
                require(
                        sa.equals(sb) && ca.statistics().equals(cb.statistics()),
                        "Fresh local CFR policy or traversal differs");
                controls.add(
                        new Control(
                                before.historyKey(h.history()),
                                s.observation(),
                                s.observationKey(),
                                hash(pa),
                                hash(ma.audit()),
                                hash(ma.strategy()),
                                CONTROL_ITERATIONS,
                                SixMaxConnectedPostflopAudit.solutionHash(sa),
                                ca.statistics()));
            }
        return new Result(
                new Report(
                        SCHEMA,
                        "VALIDATION_ONLY",
                        false,
                        cp.binding(),
                        hash(cp),
                        hash(predecessor.report()),
                        compact.hash(),
                        compact.layout(),
                        counts,
                        shares,
                        hash(diagnostics),
                        cp.solution().strategy().size(),
                        controls));
    }

    private static String hash(Object o) throws Exception {
        return SixMaxHistoryPhysicalConditionalRefinement.hash(o);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public static void write(Path path, Result result) throws Exception {
        if (Files.exists(path)) throw new IllegalArgumentException("Audit output must be new");
        SixMaxRankTexturePayoffTable.writeBytes(
                path,
                SixMaxTextureStudy.json(result.report()).getBytes(StandardCharsets.UTF_8),
                MAX_BYTES);
    }

    public static Result replay(
            Path path,
            SixMaxPreflopSolutionPack source,
            SixMaxRankTexturePayoffTable.Artifact parent,
            SixMaxHistoryPhysicalPayoffTable.Verified original,
            SixMaxHistoryPhysicalStudy.Validated predecessor,
            SixMaxHistoryPhysicalCompactStorage.Verified compact)
            throws Exception {
        var saved =
                SixMaxTexturePayoffTable.mapper()
                        .readValue(
                                SixMaxRankTexturePayoffTable.readBytes(path, MAX_BYTES),
                                Report.class);
        if (!saved.binding().equals(predecessor.checkpoint().binding())
                || !saved.compactHash().equals(compact.hash())
                || !saved.checkpointHash().equals(hash(predecessor.checkpoint()))
                || !saved.studyHash().equals(hash(predecessor.report())))
            throw new IllegalArgumentException("Storage audit lineage differs");
        var expected = assess(source, parent, original, predecessor, compact);
        if (!saved.equals(expected.report()))
            throw new IllegalArgumentException("Exact storage audit replay differs");
        return expected;
    }
}
