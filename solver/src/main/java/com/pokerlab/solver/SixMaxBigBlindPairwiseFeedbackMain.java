package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxBigBlindPairwiseFeedbackMain {
    private SixMaxBigBlindPairwiseFeedbackMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length >= 4 && Set.of("solve-review", "replay-review").contains(args[0])) {
            var input = Path.of(args[1]).toAbsolutePath().normalize();
            var report = Path.of(args[2]).toAbsolutePath().normalize();
            var review = Path.of(args[3]).toAbsolutePath().normalize();
            SixMaxTexturePayoffTableMain.distinct(List.of(input, report, review));
            boolean create = args[0].equals("solve-review");
            if (Files.exists(review) || create && Files.exists(report))
                throw new IllegalArgumentException("BB source/review outputs must be new");
            var choices = new ArrayList<SixMaxBigBlindPairwiseDrill.Choice>();
            if (args.length - 4 > SixMaxBigBlindPairwiseDrill.MAX_ATTEMPTS)
                throw new IllegalArgumentException("BB review attempt cap exceeded");
            for (int i = 4; i < args.length; i++) {
                var parts = args[i].split(":", -1);
                if (parts.length != 2
                        || !parts[0].matches("[0-7]")
                        || !Set.of("fold", "call", "check", "bet").contains(parts[1]))
                    throw new IllegalArgumentException(
                            "Choice must be bounded questionIndex:action");
                choices.add(
                        new SixMaxBigBlindPairwiseDrill.Choice(
                                Integer.parseInt(parts[0]), parts[1]));
            }
            var request = SixMaxBigBlindPairwiseFeedback.readRequest(input);
            if (choices.stream()
                    .anyMatch(c -> c.questionIndex() >= 2 * request.coverage().heroHands().size()))
                throw new IllegalArgumentException("Choice exceeds declared BB context inventory");
            var owned =
                    create
                            ? SixMaxBigBlindPairwiseFeedback.solve(request)
                            : SixMaxBigBlindPairwiseFeedback.replay(input, report);
            if (create) SixMaxBigBlindPairwiseFeedback.write(report, owned);
            if (choices.isEmpty())
                choices.addAll(SixMaxBigBlindPairwiseDrill.canonicalChoices(owned));
            SixMaxBigBlindPairwiseDrill.write(review, owned, choices);
            System.out.println(
                    "trainerAdmission=false cachedReview=true qualified="
                            + owned.report().qualifiedIndices());
            return;
        }
        if (args.length == 2 && args[0].equals("inspect")) {
            var saved = SixMaxBigBlindPairwiseFeedback.readReport(Path.of(args[1]));
            System.out.println(
                    "storedSnapshotOnly=true trainerAdmission=false qualified="
                            + saved.qualifiedIndices());
            return;
        }
        if (args.length != 3 || !Set.of("solve", "replay").contains(args[0]))
            throw new IllegalArgumentException(
                    "Usage: solve|replay <BB-feedback-request> <new-report.json[.gz]> | inspect <report>");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[2]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, output));
        boolean create = args[0].equals("solve");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Output must be new");
        var owned =
                create
                        ? SixMaxBigBlindPairwiseFeedback.solve(
                                SixMaxBigBlindPairwiseFeedback.readRequest(input))
                        : SixMaxBigBlindPairwiseFeedback.replay(input, output);
        if (create) SixMaxBigBlindPairwiseFeedback.write(output, owned);
        for (var item : owned.report().cases())
            System.out.println(
                    item.index()
                            + " qualified="
                            + item.qualifiedForOfflineDrill()
                            + " reasons="
                            + item.rejectionReasons());
        System.out.println(
                "storedSnapshotOnly=false trainerAdmission=false freshReferenceRuns=2 qualified="
                        + owned.report().qualifiedIndices()
                        + " report="
                        + SixMaxHeadsUpPreflopGame.hash(owned.report()));
    }
}
