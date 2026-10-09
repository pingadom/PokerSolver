package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class SixMaxHeadsUpPreflopArtifactTest {
    static SixMaxPreflopSolutionPack source;
    @TempDir Path temporary;

    @BeforeAll
    static void source() throws Exception {
        source = SixMaxPreflopPayoffReuseArtifactTest.pack("staged-three-nine");
    }

    @AfterAll
    static void release() {
        source = null;
    }

    ObjectNode report() throws Exception {
        return (ObjectNode)
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        SixMaxHeadsUpPreflopStudyTest.data(
                                                "three-nine-twentytwo", "report")));
    }

    ObjectNode policy() throws Exception {
        return (ObjectNode)
                SixMaxTexturePayoffTable.mapper()
                        .readTree(
                                Files.readAllBytes(
                                        SixMaxHeadsUpPreflopStudyTest.data(
                                                "three-nine-twentytwo", "policy")));
    }

    void reject(ObjectNode report, ObjectNode policy, boolean relink) throws Exception {
        if (relink)
            policy.put(
                    "reportHash",
                    SixMaxHeadsUpPreflopGame.hash(
                            SixMaxTexturePayoffTable.mapper()
                                    .treeToValue(report, SixMaxHeadsUpPreflopStudy.Report.class)));
        var r = temporary.resolve("report.json");
        var p = temporary.resolve("policy.json");
        Files.writeString(r, SixMaxTextureStudy.json(report));
        Files.writeString(p, SixMaxTextureStudy.json(policy));
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHeadsUpPreflopStudy.replay(p, r, source));
    }

    @Test
    void selfConsistentHashesCannotReplaceRecomputingOriginalLpAndEveryActionEv() throws Exception {
        var report = report();
        var constant = (ObjectNode) report.path("solve").path("projectedPayoff");
        constant.put("constant", constant.path("constant").asDouble() + 1);
        reject(report, policy(), true);
        report = report();
        var values =
                (ObjectNode)
                        report.path("decisions")
                                .get(0)
                                .path("primary")
                                .path("values")
                                .path("actionEvBb");
        String action = values.fieldNames().next();
        values.put(action, values.path(action).asDouble() + 1);
        reject(report, policy(), true);
    }

    @Test
    void selfConsistentReferenceAndPosteriorTamperingFailsFreshCompleteReplay() throws Exception {
        var report = report();
        ((ObjectNode) report.path("references").get(0)).put("solutionHash", "a".repeat(64));
        reject(report, policy(), true);
        report = report();
        ((ObjectNode) report.path("posterior").get(0)).put("conditionalProbability", .99);
        reject(report, policy(), true);
    }

    @Test
    void selfConsistentChangedPolicyCannotCreateAnOpaqueSolvedStudy() throws Exception {
        var report = report();
        var policy = policy();
        var strategy = (ObjectNode) policy.path("solution").path("strategy");
        var row = (ObjectNode) strategy.elements().next();
        var actions = new ArrayList<String>();
        row.fieldNames().forEachRemaining(actions::add);
        for (int i = 0; i < actions.size(); i++)
            row.put(actions.get(i), i == 0 ? .1 : i == 1 ? .9 : 0.);
        var solution =
                SixMaxTexturePayoffTable.mapper()
                        .treeToValue(policy.path("solution"), CfrSolution.class);
        String hash = SixMaxConnectedPostflopAudit.solutionHash(solution);
        policy.put("solutionHash", hash);
        report.put("candidateSolutionHash", hash);
        reject(report, policy, true);
    }

    @Test
    void sourceBindingPolicyBindingAndAdmissionTamperingRejectsBeforeSolving() throws Exception {
        var report = report();
        ((ObjectNode) report.path("binding")).put("sourcePackHash", "b".repeat(64));
        reject(report, policy(), false);
        var policy = policy();
        policy.put("reportHash", "c".repeat(64));
        reject(report(), policy, false);
        policy = policy();
        ((ObjectNode) policy.path("binding")).put("posteriorHash", "d".repeat(64));
        reject(report(), policy, false);
        var mapper = SixMaxTexturePayoffTable.mapper();
        for (String field :
                List.of("trainerAdmission", "schemaVersion", "evScope", "publicationStatus")) {
            var changed = report();
            if (field.equals("trainerAdmission")) changed.put(field, true);
            else changed.put(field, "unsupported");
            assertThrows(
                    Exception.class,
                    () -> mapper.treeToValue(changed, SixMaxHeadsUpPreflopStudy.Report.class));
        }
        var changed = policy();
        changed.put("trainerAdmission", true);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(changed, SixMaxHeadsUpPreflopStudy.Artifact.class));
        assertTrue(
                Arrays.stream(SixMaxHeadsUpPreflopStudy.Result.class.getDeclaredConstructors())
                        .noneMatch(c -> java.lang.reflect.Modifier.isPublic(c.getModifiers())));
    }

    @Test
    void cliRejectsAliasesHardlinksAndExistingOutputsBeforeLoadingMalformedInputs()
            throws Exception {
        Path source = temporary.resolve("source.json"),
                spec = temporary.resolve("spec.json"),
                policy = temporary.resolve("policy.json"),
                report = temporary.resolve("report.json");
        Files.writeString(source, "malformed source");
        Files.writeString(spec, "malformed specification");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopStudyMain.main(
                                new String[] {
                                    "solve",
                                    source.toString(),
                                    spec.toString(),
                                    temporary.resolve("./source.json").toString(),
                                    report.toString()
                                }));
        var link = temporary.resolve("linked.json");
        Files.createLink(link, source);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopStudyMain.main(
                                new String[] {
                                    "solve",
                                    source.toString(),
                                    link.toString(),
                                    policy.toString(),
                                    report.toString()
                                }));
        Files.writeString(policy, "existing policy");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopStudyMain.main(
                                new String[] {
                                    "solve",
                                    source.toString(),
                                    spec.toString(),
                                    policy.toString(),
                                    report.toString()
                                }));
        assertEquals("existing policy", Files.readString(policy));
        assertEquals("malformed source", Files.readString(source));
        assertFalse(Files.exists(report));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopStudyMain.main(
                                new String[] {
                                    "replay",
                                    source.toString(),
                                    policy.toString(),
                                    source.toString()
                                }));
        for (var args :
                List.of(
                        new String[] {},
                        new String[] {"solve"},
                        new String[] {"replay"},
                        new String[] {"unknown", "a", "b", "c"}))
            assertThrows(
                    IllegalArgumentException.class, () -> SixMaxHeadsUpPreflopStudyMain.main(args));
    }

    @Test
    void expandedEvidenceCapAndStrictJsonRejectBeforeAcceptingAnyHandle() throws Exception {
        var huge = temporary.resolve("huge.json.gz");
        try (var out = new java.util.zip.GZIPOutputStream(Files.newOutputStream(huge))) {
            out.write(new byte[SixMaxHeadsUpPreflopStudy.MAX_BYTES + 1]);
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxHeadsUpPreflopStudy.replay(
                                temporary.resolve("absent-policy.json"), huge, source));
        var mapper = SixMaxTexturePayoffTable.mapper();
        var valid = Files.readString(SixMaxHeadsUpPreflopStudyTest.data("three-nine", "report"));
        assertThrows(
                Exception.class,
                () -> mapper.readValue(valid + " {}", SixMaxHeadsUpPreflopStudy.Report.class));
        assertThrows(
                Exception.class,
                () ->
                        mapper.readValue(
                                valid.replaceFirst("\\{", "{\"trainerAdmission\":false,"),
                                SixMaxHeadsUpPreflopStudy.Report.class));
        var altered = report();
        altered.put("unexpected", 1);
        assertThrows(
                Exception.class,
                () -> mapper.treeToValue(altered, SixMaxHeadsUpPreflopStudy.Report.class));
    }

    @Test
    void rejectedStudyCannotAcquireAStandInPolicyDuringReplay() throws Exception {
        var report = SixMaxHeadsUpPreflopStudyTest.data("five-target", "report");
        var policy = temporary.resolve("policy.json");
        Files.writeString(policy, "{}");
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxHeadsUpPreflopStudy.replay(policy, report, source));
    }
}
