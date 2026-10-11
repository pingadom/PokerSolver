package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Physical qualification is replayed once at startup, never while asking or grading the drill. */
public final class SixMaxSuppliedIntervalTrainerMain {
    private SixMaxSuppliedIntervalTrainerMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4
                || !Set.of("create", "replay").contains(args[0])
                || (args[0].equals("replay") && args.length != 4)
                || args.length > 4 + SixMaxSuppliedIntervalTrainer.MAX_ATTEMPTS)
            throw new IllegalArgumentException(
                    "Usage: create <request> <report> <new-drill.json[.gz]> [action ...]"
                            + " | replay <request> <report> <drill.json[.gz]>");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                report = Path.of(args[2]).toAbsolutePath().normalize(),
                drill = Path.of(args[3]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report, drill));
        boolean create = args[0].equals("create");
        if (create && Files.exists(drill))
            throw new IllegalArgumentException("Interval drill output must be a new path");
        var saved = create ? null : SixMaxSuppliedIntervalTrainer.read(drill);
        var trainer =
                new SixMaxSuppliedIntervalTrainer(
                        SixMaxSuppliedIntervalFeedback.replay(request, report));
        var review =
                create
                        ? trainer.review(
                                trainer.question(), Arrays.asList(args).subList(4, args.length))
                        : trainer.replay(saved);
        if (create) trainer.write(drill, review);
        System.out.println(SixMaxTextureStudy.json(review));
        System.out.println(
                "trainerAdmission=false selectedQuestions=1 attempts=" + review.attempts().size());
    }
}
