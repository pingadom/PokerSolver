package com.pokerlab.solver;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WeakHandFlopTrainerTest {
    static final Path REQUEST =
            Path.of("../docs/data/supplied-weak-hand-qualified-flop-request.json");
    static final Path REPORT =
            Path.of("../docs/data/supplied-weak-hand-qualified-flop-report.json.gz");
    static SixMaxWeakHandFlopTrainer trainer;

    @BeforeAll
    static void loadFullPinnedQualificationOnce() throws Exception {
        trainer =
                SixMaxWeakHandFlopTrainer.load(
                        REQUEST, REPORT, SixMaxWeakHandFlopTrainer.REPORT_HASH);
    }

    @Test
    void qualifiedFoldAndBadCallProduceTheExactOwnedReview() throws Exception {
        var q = trainer.question();
        assertEquals("2c 3c", q.heroCards());
        assertEquals("BTN", q.hero());
        assertEquals(6, q.context().players().size());
        assertEquals(3.25, q.context().toCallBb());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.WITHIN_LIMIT,
                trainer.grade(q, "fold").selected().classification());
        assertEquals(
                SixMaxSuppliedIntervalFeedback.Classification.OUTSIDE_LIMIT,
                trainer.grade(q, "call").selected().classification());
        assertTrue(trainer.grade(q, "call").selected().lowerLossBb() > 2.24);
        assertEquals(
                SixMaxRefinedFlopTrainer.read(
                        Path.of("../docs/data/supplied-weak-hand-qualified-flop-drill.json")),
                trainer.review(q, List.of("fold", "call")));
        assertEquals(
                100, trainer.review(q, Collections.nCopies(100, "fold")).counts().withinLimit());
        assertFalse(q.trainerAdmission());
    }

    @Test
    void foreignQuestionsAndForgedSavedGradesDoNotGrantAuthority(@TempDir Path dir)
            throws Exception {
        var m = SixMaxTexturePayoffTable.mapper();
        var q = trainer.question();
        var tree = (ObjectNode) m.valueToTree(q);
        tree.put("heroCards", "As Qs");
        var foreign = m.treeToValue(tree, SixMaxRefinedFlopTrainer.Question.class);
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(foreign, "fold"));
        assertThrows(IllegalArgumentException.class, () -> trainer.grade(q, "check"));
        assertThrows(
                IllegalArgumentException.class,
                () -> trainer.review(q, Collections.nCopies(101, "fold")));
        var review = trainer.review(q, List.of("fold", "call"));
        var changed = (ObjectNode) m.valueToTree(review);
        ((ObjectNode) changed.path("counts")).put("withinLimit", 2);
        var forged = m.treeToValue(changed, SixMaxRefinedFlopTrainer.Review.class);
        assertThrows(IllegalArgumentException.class, () -> trainer.replay(forged));
        var file = dir.resolve("review.json.gz");
        trainer.write(file, review);
        assertEquals(review, trainer.replay(file));
        assertThrows(FileAlreadyExistsException.class, () -> trainer.write(file, review));
    }

    @Test
    void exactSourcePinAndPrivateConstructionPreventSubstitution() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SixMaxWeakHandFlopTrainer.load(REQUEST, REPORT, "0".repeat(64)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxWeakHandFlopTrainer.load(
                                REQUEST, REQUEST, SixMaxWeakHandFlopTrainer.REPORT_HASH));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SixMaxWeakHandFlopTrainer.load(
                                REQUEST,
                                Path.of(
                                        "../docs/data/supplied-paired-btn-facing-bet-feedback-report.json.gz"),
                                SixMaxWeakHandFlopTrainer.REPORT_HASH));
        assertTrue(
                Arrays.stream(SixMaxWeakHandFlopTrainer.class.getDeclaredConstructors())
                        .allMatch(c -> java.lang.reflect.Modifier.isPrivate(c.getModifiers())));
    }
}
