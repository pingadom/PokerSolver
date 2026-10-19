package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class RefinedFlopTrainerTest {
    static SixMaxRefinedFlopTrainer trainer;

    @BeforeAll
    static void setup() throws Exception {
        trainer = new SixMaxRefinedFlopTrainer(RefinedFlopOwnedFixture.owned());
    }

    @Test
    void cachedQuestionAndBothGradesMatchTheReplayableSavedDrill() throws Exception {
        var q = trainer.question();
        assertEquals("BTN", q.hero());
        assertEquals("As Qs", q.heroCards());
        assertEquals(3.25, q.context().toCallBb());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                trainer.grade(q, "fold").selected().classification());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                trainer.grade(q, "call").selected().classification());
        assertEquals(
                SixMaxRefinedFlopTrainer.read(
                        Path.of("../docs/data/supplied-paired-btn-facing-bet-drill.json")),
                trainer.review(q, List.of("fold", "call")));
        assertEquals(
                100, trainer.review(q, Collections.nCopies(100, "call")).counts().withinLimit());
        assertEquals(0, trainer.review(q, List.of()).attempts().size());
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(q, "check"));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(q, Collections.nCopies(101, "fold")));
    }

    @Test
    void callerEditedQuestionsGradesAndCountsCannotAuthorizeFeedback(@TempDir Path temp)
            throws Exception {
        var mapper = SixMaxTexturePayoffTable.mapper();
        var question = (ObjectNode) mapper.valueToTree(trainer.question());
        question.put("heroCards", "Ah Kh");
        var changed = mapper.treeToValue(question, SixMaxRefinedFlopTrainer.Question.class);
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(changed, "call"));
        var review =
                (ObjectNode)
                        mapper.valueToTree(trainer.review(trainer.question(), List.of("fold")));
        ((ObjectNode) review.path("counts")).put("withinLimit", 100);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        trainer.replay(
                                mapper.treeToValue(review, SixMaxRefinedFlopTrainer.Review.class)));
        var file = temp.resolve("review.json.gz");
        var saved = trainer.review(trainer.question(), List.of("fold", "call"));
        trainer.write(file, saved);
        assertEquals(saved, trainer.replay(file));
        assertThrows(FileAlreadyExistsException.class, () -> trainer.write(file, saved));
    }

    @Test
    void loaderRejectsMissingPinsAndARehashedEditedReportAfterFullReplay(@TempDir Path temp)
            throws Exception {
        var input = RefinedFlopOwnedFixture.INPUT;
        var report = RefinedFlopOwnedFixture.REPORT;
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxRefinedFlopTrainerLoader.load(input, report, "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxRefinedFlopTrainerLoader.load(
                                input, input, trainer.question().reportHash()));
        var tree =
                (ObjectNode)
                        SixMaxTexturePayoffTable.mapper()
                                .valueToTree(SixMaxRefinedFlopFeedback.readReport(report));
        ((ObjectNode) tree.path("primary").path("actionUtilitiesBb")).put("call", 9.);
        var edited = temp.resolve("edited.json");
        Files.writeString(edited, tree.toString());
        var repinned = SixMaxHeadsUpPreflopGame.hash(SixMaxRefinedFlopFeedback.readReport(edited));
        assertEquals(
                "Complete refined feedback replay differs",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> SixMaxRefinedFlopTrainerLoader.load(input, edited, repinned))
                        .getMessage());
    }
}
