package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** One pinned complete source replay at startup, then cached mixed-question grades and review. */
public final class SixMaxPairedFlopTrainerMain {
    private SixMaxPairedFlopTrainerMain() {}

    static List<SixMaxPairedFlopTrainer.Choice> choices(String[] args) {
        var result = new ArrayList<SixMaxPairedFlopTrainer.Choice>();
        for (String arg : args) {
            if (!arg.matches("[0-3]:(fold|call)"))
                throw new IllegalArgumentException("Choice must be <0..3>:<fold|call>");
            result.add(new SixMaxPairedFlopTrainer.Choice(arg.charAt(0) - '0', arg.substring(2)));
        }
        if (result.size() > SixMaxPairedFlopTrainer.MAX_ATTEMPTS)
            throw new IllegalArgumentException("At most 100 choices required");
        return List.copyOf(result);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 5
                || !Set.of("create", "replay").contains(args[0])
                || (args[0].equals("replay") && args.length != 5)
                || args.length > 5 + SixMaxPairedFlopTrainer.MAX_ATTEMPTS)
            throw new IllegalArgumentException(
                    "Usage: create <request> <report> <report-hash>"
                            + " <new-review.json[.gz]> [index:action ...]"
                            + " | replay <request> <report> <report-hash> <review.json[.gz]>");
        Path request = Path.of(args[1]).toAbsolutePath().normalize(),
                report = Path.of(args[2]).toAbsolutePath().normalize(),
                reviewPath = Path.of(args[4]).toAbsolutePath().normalize();
        SixMaxTexturePayoffTableMain.distinct(List.of(request, report, reviewPath));
        boolean create = args[0].equals("create");
        if (create && Files.exists(reviewPath))
            throw new IllegalArgumentException("paired BTN drill output must be a new path");
        var submitted = choices(Arrays.copyOfRange(args, 5, args.length));
        var saved = create ? null : SixMaxPairedFlopTrainer.read(reviewPath);
        var trainer = SixMaxPairedFlopTrainerLoader.load(request, report, args[3]);
        var review = create ? trainer.review(submitted) : trainer.replay(saved);
        if (create) trainer.write(reviewPath, review);
        System.out.println(SixMaxTextureStudy.json(review));
        System.out.println(
                "trainerAdmission=false selectedQuestions=4 attempts=" + review.attempts().size());
    }
}
