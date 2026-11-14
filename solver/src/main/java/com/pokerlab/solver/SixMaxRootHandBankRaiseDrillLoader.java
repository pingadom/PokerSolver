package com.pokerlab.solver;

import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Capture and validate every input, reproduce once, then expose only immutable cached choices. */
public final class SixMaxRootHandBankRaiseDrillLoader {
    public static final String SCHEMA = "pokerlab-bank-raise-drill-manifest/v1";
    public static final String STUDY_HASH =
            "60f8eb975a963ebdf3964cf4edb72354b8e0841e01bc5eb3a3a333e5bc9b0343";
    public static final int MAX_MANIFEST_BYTES = 16 * 1024;
    public static final String REQUEST_SHA =
            "8f9f0b0947a5786fd59a64f62b083113dd71493e95149159d2c6b84578962be6";

    public record StartupBudget(
            int physicalGames,
            int maximumParentLpSolves,
            int maximumEndpointLpSolves,
            int freshReferenceRuns,
            int freshIterations) {
        public StartupBudget {
            if (physicalGames != 1
                    || maximumParentLpSolves != 4
                    || maximumEndpointLpSolves != 2
                    || freshReferenceRuns != 2
                    || freshIterations != 240000)
                throw new IllegalArgumentException(
                        "Complete fixed feedback startup budget required");
        }

        public static StartupBudget bounded() {
            return new StartupBudget(1, 4, 2, 2, 240000);
        }
    }

    public record Manifest(
            String schemaVersion,
            String studyHash,
            String requestFile,
            String requestSha256,
            String reportFile,
            String reportStoredSha256,
            String minimumFile,
            String minimumStoredSha256,
            String bankFile,
            String bankStoredSha256,
            StartupBudget startupBudget) {
        public Manifest {
            if (!SCHEMA.equals(schemaVersion)
                    || !STUDY_HASH.equals(studyHash)
                    || !file(requestFile)
                    || !file(reportFile)
                    || !file(minimumFile)
                    || !file(bankFile)
                    || !REQUEST_SHA.equals(requestSha256)
                    || !"7077bd260e422690e0060a31d36dea53e545c8ee88641ef8e5e811633d9723e2"
                            .equals(reportStoredSha256)
                    || !SixMaxRootHandBankRaiseFeedback.SOURCE_SHA.equals(minimumStoredSha256)
                    || !SixMaxRootHandBankRaiseStudy.BANK_SHA.equals(bankStoredSha256)
                    || startupBudget == null)
                throw new IllegalArgumentException(
                        "Pinned feedback sources and complete budget required");
        }
    }

    private record Captured(
            Path path,
            Path realPath,
            Object fileKey,
            long size,
            String modified,
            String sha256,
            byte[] bytes) {}

    private SixMaxRootHandBankRaiseDrillLoader() {}

    private static boolean file(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,180}");
    }

    private static Captured capture(Path path, Path root, int cap) throws Exception {
        if (Files.isSymbolicLink(path))
            throw new IllegalArgumentException("Symbolic startup sources are not accepted");
        Path real = path.toRealPath();
        if (!root.equals(real.getParent()))
            throw new IllegalArgumentException(
                    "Root hand bank dependency escapes manifest directory");
        var before = Files.readAttributes(real, BasicFileAttributes.class);
        if (!before.isRegularFile() || before.size() > cap)
            throw new IllegalArgumentException("Bounded regular root hand bank source required");
        byte[] bytes;
        try (var stream = Files.newInputStream(real)) {
            bytes = stream.readNBytes(cap + 1);
        }
        var after = Files.readAttributes(real, BasicFileAttributes.class);
        if (bytes.length > cap
                || bytes.length != before.size()
                || before.size() != after.size()
                || !Objects.equals(before.fileKey(), after.fileKey())
                || !before.lastModifiedTime().equals(after.lastModifiedTime()))
            throw new IllegalArgumentException("Root hand bank source changed while reading");
        return new Captured(
                path,
                real,
                after.fileKey(),
                after.size(),
                after.lastModifiedTime().toString(),
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                bytes);
    }

    private static byte[] expanded(Captured source) throws Exception {
        if (!source.path().toString().endsWith(".gz")) return source.bytes();
        try (var gzip = new GZIPInputStream(new ByteArrayInputStream(source.bytes()))) {
            byte[] expanded = gzip.readNBytes(SixMaxRootHandBankStudy.MAX_BYTES + 1);
            if (expanded.length > SixMaxRootHandBankStudy.MAX_BYTES)
                throw new IllegalArgumentException("Expanded root hand bank source cap exceeded");
            return expanded;
        }
    }

    public static Manifest readManifest(Path path) throws Exception {
        path = path.toAbsolutePath().normalize();
        var captured = capture(path, path.getParent().toRealPath(), MAX_MANIFEST_BYTES);
        return SixMaxTexturePayoffTable.mapper().readValue(captured.bytes(), Manifest.class);
    }

    public static SixMaxRootHandBankRaiseDrill.Result load(Path path, String expectedStudyHash)
            throws Exception {
        if (path == null || !STUDY_HASH.equals(expectedStudyHash))
            throw new IllegalArgumentException(
                    "Manifest and exact published root hand bank pin required");
        path = path.toAbsolutePath().normalize();
        Path root = path.getParent().toRealPath();
        var manifestBytes = capture(path, root, MAX_MANIFEST_BYTES);
        var mapper = SixMaxTexturePayoffTable.mapper();
        var manifest = mapper.readValue(manifestBytes.bytes(), Manifest.class);
        var requestBytes = capture(root.resolve(manifest.requestFile()), root, 128 * 1024);
        var reportBytes =
                capture(
                        root.resolve(manifest.reportFile()),
                        root,
                        SixMaxRootHandBankStudy.MAX_BYTES);
        var minimumBytes =
                capture(
                        root.resolve(manifest.minimumFile()),
                        root,
                        SixMaxRootHandBankRaiseFeedback.MAX_BYTES);
        var bankBytes =
                capture(
                        root.resolve(manifest.bankFile()),
                        root,
                        SixMaxRootHandBankRaiseFeedback.MAX_BYTES);
        var sources = List.of(manifestBytes, requestBytes, reportBytes, minimumBytes, bankBytes);
        for (int i = 0; i < sources.size(); i++)
            for (int j = i + 1; j < sources.size(); j++)
                if (Files.isSameFile(sources.get(i).path(), sources.get(j).path()))
                    throw new IllegalArgumentException("Aliased feedback startup sources");
        if (!manifest.requestSha256().equals(requestBytes.sha256())
                || !manifest.reportStoredSha256().equals(reportBytes.sha256())
                || !manifest.minimumStoredSha256().equals(minimumBytes.sha256())
                || !manifest.bankStoredSha256().equals(bankBytes.sha256()))
            throw new IllegalArgumentException("Feedback source byte pin differs");
        var request =
                mapper.readValue(
                        expanded(requestBytes), SixMaxRootHandBankRaiseFeedback.Request.class);
        var saved =
                mapper.readValue(
                        expanded(reportBytes), SixMaxRootHandBankRaiseFeedback.Report.class);
        if (!request.equals(saved.request())
                || !expectedStudyHash.equals(SixMaxHeadsUpPreflopGame.hash(saved))
                || !saved.qualifiedForOfflineDrill()
                || saved.trainerAdmission())
            throw new IllegalArgumentException(
                    "Qualified feedback identity differs before startup");
        var minimum =
                mapper.readValue(expanded(minimumBytes), SixMaxRootHandBankRaiseStudy.Report.class);
        var bank = mapper.readValue(expanded(bankBytes), SixMaxRootHandBankStudy.Report.class);
        if (!saved.sourceMinimum().equals(minimum) || !minimum.sourceBank().equals(bank))
            throw new IllegalArgumentException("Complete captured feedback lineage differs");
        var owned =
                SixMaxRootHandBankRaiseFeedback.solve(
                        request, bankBytes.path(), minimumBytes.path());
        if (!saved.equals(owned.report()))
            throw new IllegalArgumentException("Complete owned feedback startup replay differs");
        var result = SixMaxRootHandBankRaiseDrill.create(owned);
        if (!expectedStudyHash.equals(result.snapshot().studyHash())
                || result.snapshot().questions().size() != 2)
            throw new IllegalArgumentException("Owned feedback cache inventory differs");
        for (var before : sources) {
            var after =
                    capture(
                            before.path(),
                            root,
                            before == manifestBytes
                                    ? MAX_MANIFEST_BYTES
                                    : SixMaxRootHandBankStudy.MAX_BYTES);
            if (!before.realPath().equals(after.realPath())
                    || !Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.modified().equals(after.modified())
                    || !before.sha256().equals(after.sha256())
                    || !Arrays.equals(before.bytes(), after.bytes()))
                throw new IllegalArgumentException(
                        "Root hand bank sources changed during complete startup");
        }
        return result;
    }
}
