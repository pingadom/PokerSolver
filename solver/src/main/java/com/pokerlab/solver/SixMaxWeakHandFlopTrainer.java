package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.List;

/** One pinned weak-hand source; only complete owned physical qualification constructs its cache. */
public final class SixMaxWeakHandFlopTrainer {
    public static final String SOURCE = "QUALIFIED_2C3C_BTN_PAIRED_FLOP_FACING_BET/v1";
    public static final String REPORT_HASH =
            "5dbd0cdeba8a74e6da32dae0bddeb69381a90ba4f72220ab4a9bf92bcc667c56";
    private final SixMaxRefinedFlopTrainer trainer;

    private SixMaxWeakHandFlopTrainer(SixMaxRefinedFlopTrainer trainer) {
        var q = trainer.question();
        if (!REPORT_HASH.equals(q.reportHash())
                || !q.hero().equals("BTN")
                || !q.heroCards().equals("2c 3c")
                || q.maximumLossBb() != .01
                || !q.context().board().equals(List.of("8d", "Kc", "Kd"))
                || !q.context().legalActions().equals(List.of("fold", "call"))
                || q.context().players().size() != 6
                || q.context().toCallBb() != 3.25)
            throw new IllegalArgumentException("Qualified weak-hand source identity differs");
        this.trainer = trainer;
    }

    public static SixMaxWeakHandFlopTrainer load(Path request, Path report, String pin)
            throws Exception {
        if (!REPORT_HASH.equals(pin))
            throw new IllegalArgumentException("Exact weak-hand qualification pin required");
        return new SixMaxWeakHandFlopTrainer(
                SixMaxRefinedFlopTrainerLoader.load(request, report, pin));
    }

    public SixMaxRefinedFlopTrainer.Question question() {
        return trainer.question();
    }

    public SixMaxRefinedFlopTrainer.Feedback grade(
            SixMaxRefinedFlopTrainer.Question question, String action) {
        return trainer.grade(question, action);
    }

    public SixMaxRefinedFlopTrainer.Review review(
            SixMaxRefinedFlopTrainer.Question question, List<String> actions) {
        return trainer.review(question, actions);
    }

    public SixMaxRefinedFlopTrainer.Review replay(SixMaxRefinedFlopTrainer.Review review) {
        return trainer.replay(review);
    }

    public SixMaxRefinedFlopTrainer.Review replay(Path path) throws Exception {
        return trainer.replay(path);
    }

    public void write(Path path, SixMaxRefinedFlopTrainer.Review review) throws Exception {
        trainer.write(path, review);
    }
}
