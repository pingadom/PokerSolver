package com.pokerlab.solver;

import java.nio.file.Path;
import java.util.*;

/** Small playable offline drill; every command first replays the complete conditional study. */
public final class SixMaxSuppliedRangePreflopTrainerMain {
    private SixMaxSuppliedRangePreflopTrainerMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 1
                || !((args[0].equals("question") || args[0].equals("session")) && args.length == 5
                        || args[0].equals("grade") && args.length == 6
                        || args[0].equals("review") && args.length == 15))
            throw new IllegalArgumentException(
                    "Usage: question|session|grade|review <supplied-input.json> <policy.json[.gz]> <report.json[.gz]> <seed> [action | ten session actions]");
        long seed = Long.parseLong(args[4]);
        var paths = new ArrayList<Path>();
        for (int i = 1; i <= 3; i++) paths.add(Path.of(args[i]).toAbsolutePath().normalize());
        SixMaxTexturePayoffTableMain.distinct(paths);
        var trainer =
                new SixMaxSuppliedRangePreflopTrainer(
                        SixMaxSuppliedRangePreflopStudy.replay(
                                paths.get(0), paths.get(1), paths.get(2)));
        Object output =
                switch (args[0]) {
                    case "question" -> trainer.question(seed);
                    case "session" ->
                            java.util.stream.IntStream.range(
                                            0, SixMaxSuppliedRangePreflopTrainer.SESSION_LENGTH)
                                    .mapToObj(i -> trainer.sessionQuestion(seed, i))
                                    .toList();
                    case "grade" -> trainer.grade(trainer.question(seed), args[5]);
                    case "review" ->
                            trainer.review(
                                    seed, trainer.studyHash(), Arrays.asList(args).subList(5, 15));
                    default -> throw new IllegalArgumentException("Unknown trainer command");
                };
        System.out.print(SixMaxTextureStudy.json(output));
    }
}
