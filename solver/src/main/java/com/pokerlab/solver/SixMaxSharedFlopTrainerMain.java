package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

public final class SixMaxSharedFlopTrainerMain {
    private SixMaxSharedFlopTrainerMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 5
                || args.length > 5 + SixMaxSharedFlopTrainer.MAX_ATTEMPTS
                || !Set.of("review", "replay-review").contains(args[0])
                || (args[0].equals("replay-review") && args.length != 5))
            throw new IllegalArgumentException(
                    "Usage: review|replay-review <request> <report> <pin> <review> [index:action ...]");
        var input = Path.of(args[1]).toAbsolutePath().normalize();
        var report = Path.of(args[2]).toAbsolutePath().normalize();
        var review = Path.of(args[4]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(input, report, review));
        var choices = new ArrayList<SixMaxSharedFlopTrainer.Choice>();
        for (int i = 5; i < args.length; i++) {
            var pair = args[i].split(":", -1);
            if (pair.length != 2 || !Set.of("fold", "call").contains(pair[1]))
                throw new IllegalArgumentException("Explicit question index:fold|call required");
            choices.add(new SixMaxSharedFlopTrainer.Choice(Integer.parseInt(pair[0]), pair[1]));
        }
        if (args[0].equals("review") && Files.exists(review))
            throw new IllegalArgumentException("Review output must be new");
        var trainer = SixMaxSharedFlopTrainerLoader.load(input, report, args[3]);
        var result = args[0].equals("review") ? trainer.review(choices) : trainer.replay(review);
        if (args[0].equals("review")) trainer.write(review, result);
        System.out.println(
                "trainerAdmission=false cachedQuestions="
                        + trainer.questions().size()
                        + " attempts="
                        + result.attempts().size()
                        + " distinctQuestions="
                        + result.counts().distinctQuestions());
    }
}
