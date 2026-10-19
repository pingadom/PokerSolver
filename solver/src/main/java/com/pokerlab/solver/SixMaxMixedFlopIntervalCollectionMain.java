package com.pokerlab.solver;

import java.nio.file.*;
import java.util.*;

/** Offline collection review. Every source physically replays once before cached interaction. */
public final class SixMaxMixedFlopIntervalCollectionMain {
    private SixMaxMixedFlopIntervalCollectionMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 4
                || !Set.of("create", "replay").contains(args[0])
                || (args[0].equals("replay") && args.length != 4)
                || args.length > 4 + SixMaxMixedFlopIntervalCollection.MAX_ATTEMPTS)
            throw new IllegalArgumentException(
                    "Usage: create <manifest> <collection-hash> <new-review.json> [index:action ...]"
                            + " | replay <manifest> <collection-hash> <review.json>");
        var manifest = Path.of(args[1]).toAbsolutePath().normalize();
        var output = Path.of(args[3]).toAbsolutePath().normalize();
        SixMaxMixedFlopIntervalCollectionLoader.distinctOutput(manifest, output);
        boolean create = args[0].equals("create");
        if (create && Files.exists(output))
            throw new IllegalArgumentException("Collection review output must be a new path");
        var saved = create ? null : SixMaxMixedFlopIntervalCollection.read(output);
        // Parse choices before expensive replay. The collection validates indices/actions/pins.
        var indices = new ArrayList<Integer>();
        var actions = new ArrayList<String>();
        for (int i = 4; i < args.length; i++) {
            int colon = args[i].indexOf(':');
            if (colon < 1 || colon == args[i].length() - 1)
                throw new IllegalArgumentException("Explicit questionIndex:action required");
            int index = Integer.parseInt(args[i].substring(0, colon));
            if (index < 0 || index >= SixMaxMixedFlopIntervalCollection.MAX_QUESTIONS)
                throw new IllegalArgumentException("Invalid question index");
            String action = args[i].substring(colon + 1);
            if (!(index == 1 ? Set.of("fold", "call") : Set.of("check", "bet")).contains(action))
                throw new IllegalArgumentException("Illegal move for mixed question");
            indices.add(index);
            actions.add(args[i].substring(colon + 1));
        }
        var collection = SixMaxMixedFlopIntervalCollectionLoader.load(manifest, args[2]);
        var choices = new ArrayList<SixMaxMixedFlopIntervalCollection.Choice>();
        for (int i = 0; i < indices.size(); i++)
            choices.add(
                    new SixMaxMixedFlopIntervalCollection.Choice(
                            indices.get(i),
                            collection.question(indices.get(i)).decision().reportHash(),
                            actions.get(i)));
        var review = create ? collection.review(choices) : collection.replay(saved);
        if (create) collection.write(output, review);
        System.out.println(SixMaxTextureStudy.json(review));
        System.out.println(
                "trainerAdmission=false selectedQuestions="
                        + collection.questions().size()
                        + " distinctAttempted="
                        + review.counts().distinctQuestionsAttempted()
                        + " attempts="
                        + review.attempts().size());
    }
}
