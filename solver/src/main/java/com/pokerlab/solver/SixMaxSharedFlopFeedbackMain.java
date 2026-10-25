package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSharedFlopFeedbackMain {
    private SixMaxSharedFlopFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length >= 4 && args[0].equals("solve-review")) {
            var choices = new ArrayList<SixMaxSharedFlopTrainer.Choice>();
            if (args.length > 4 + SixMaxSharedFlopTrainer.MAX_ATTEMPTS)
                throw new IllegalArgumentException("At most 100 explicit review choices required");
            for (int i = 4; i < args.length; i++) {
                var pair = args[i].split(":", -1);
                if (pair.length != 2
                        || !pair[0].matches("0|[1-9][0-9]*")
                        || !Set.of("fold", "call").contains(pair[1]))
                    throw new IllegalArgumentException(
                            "Explicit question index:fold|call required");
                choices.add(new SixMaxSharedFlopTrainer.Choice(Integer.parseInt(pair[0]), pair[1]));
            }
            var result = solveReview(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), choices);
            summary(result);
            return;
        }
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <shared-request> <new-report.json[.gz]>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var owned =
                create
                        ? SixMaxSharedFlopFeedback.solve(
                                SixMaxSharedFlopFeedback.readRequest(input))
                        : SixMaxSharedFlopFeedback.replay(input, output);
        if (create) SixMaxSharedFlopFeedback.write(output, owned);
        summary(owned);
    }

    /** Complete qualification once; its private capability also authorizes the paired review. */
    static SixMaxSharedFlopFeedback.Result solveReview(
            Path requestPath,
            Path reportPath,
            Path reviewPath,
            List<SixMaxSharedFlopTrainer.Choice> choices)
            throws Exception {
        requestPath = requestPath.toAbsolutePath().normalize();
        reportPath = reportPath.toAbsolutePath().normalize();
        reviewPath = reviewPath.toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath, reviewPath));
        if (Files.exists(reportPath, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(reviewPath, LinkOption.NOFOLLOW_LINKS))
            throw new IllegalArgumentException("Both output paths must be new");
        var request = SixMaxSharedFlopFeedback.readRequest(requestPath);
        if (choices == null
                || choices.size() > SixMaxSharedFlopTrainer.MAX_ATTEMPTS
                || choices.stream()
                        .anyMatch(
                                c ->
                                        c == null
                                                || c.questionIndex() < 0
                                                || c.questionIndex() >= request.heroHands().size()
                                                || c.action() == null
                                                || !Set.of("fold", "call").contains(c.action())))
            throw new IllegalArgumentException("Review choices must belong to the complete batch");
        choices = List.copyOf(choices);
        Files.createDirectories(reportPath.getParent());
        Files.createDirectories(reviewPath.getParent());
        requestPath = requestPath.toRealPath();
        reportPath = reportPath.getParent().toRealPath().resolve(reportPath.getFileName());
        reviewPath = reviewPath.getParent().toRealPath().resolve(reviewPath.getFileName());
        SixMaxTexturePayoffTableMain.distinct(List.of(requestPath, reportPath, reviewPath));
        // Every input/choice/output preflight above precedes expensive optimizer/reference work.
        var owned = SixMaxSharedFlopFeedback.solve(request);
        if (owned.qualifiedBatch().isEmpty()) {
            // Preserve failed evidence, but never manufacture a review or grading capability.
            SixMaxSharedFlopFeedback.write(reportPath, owned);
            throw new IllegalArgumentException(
                    "Batch rejected; report retained, no review created");
        }
        var trainer = new SixMaxSharedFlopTrainer(owned);
        var review = trainer.review(choices);
        if (SixMaxTextureStudy.json(review).getBytes(java.nio.charset.StandardCharsets.UTF_8).length
                > SixMaxSharedFlopTrainer.MAX_BYTES)
            throw new IllegalArgumentException("Review exceeds original expanded byte cap");
        SixMaxSharedFlopFeedback.write(reportPath, owned);
        trainer.write(reviewPath, review);
        return owned;
    }

    private static void summary(SixMaxSharedFlopFeedback.Result owned) throws Exception {
        System.out.println(
                "trainerAdmission=false qualified="
                        + owned.report().qualifiedForOfflineDrill()
                        + " questions="
                        + owned.report().cases().size()
                        + " freshReferenceRuns=2 report="
                        + SixMaxHeadsUpPreflopGame.hash(owned.report()));
    }
}
