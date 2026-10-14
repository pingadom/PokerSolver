package com.pokerlab.api;

import com.pokerlab.solver.SixMaxFlopIntervalCollection;
import com.pokerlab.solver.SixMaxSuppliedFlopActionBounds;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback;
import com.pokerlab.solver.SixMaxSuppliedRangePreflopGame;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Separate ordered-collection protocol. Client choices cannot supply questions, EVs or grades. */
@RestController
@RequestMapping("/api/v1/trainer/research/flop-interval-collection")
@ConditionalOnProperty(
        name = "pokerlab.trainer.flop-interval-collection-enabled",
        havingValue = "true")
public class FlopIntervalCollectionController {
    public record Metadata(
            String collectionHash,
            String publicationStatus,
            boolean trainerAdmission,
            String selection,
            String model,
            String chanceModel,
            String evScope,
            String reachUnits,
            String beliefs,
            String sourceHistoryReachStatus,
            List<SixMaxFlopIntervalCollection.Kind> questionKinds,
            int availableQuestions,
            int activePlayers,
            int maximumAttempts) {
        public Metadata {
            questionKinds = List.copyOf(questionKinds);
        }
    }

    public record GradeRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String collectionHash,
            @NotNull @Min(0) @Max(SixMaxFlopIntervalCollection.MAX_QUESTIONS - 1)
                    Integer questionIndex,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    public record SubmittedChoice(
            @NotNull @Min(0) @Max(SixMaxFlopIntervalCollection.MAX_QUESTIONS - 1)
                    Integer questionIndex,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String collectionHash,
            @NotNull @Size(max = SixMaxFlopIntervalCollection.MAX_ATTEMPTS)
                    List<@NotNull @Valid SubmittedChoice> choices) {}

    private final SixMaxFlopIntervalCollection collection;

    public FlopIntervalCollectionController(SixMaxFlopIntervalCollection collection) {
        this.collection = collection;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        return response(
                new Metadata(
                        collection.collectionHash(),
                        SixMaxFlopIntervalCollection.STATUS,
                        false,
                        SixMaxFlopIntervalCollection.SELECTION,
                        SixMaxSuppliedFlopBettingGame.MODEL,
                        SixMaxSuppliedFlopBettingGame.CHANCE,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                        SixMaxSuppliedRangePreflopGame.BELIEFS,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        collection.questions().stream().map(q -> q.decision().kind()).toList(),
                        collection.questions().size(),
                        2,
                        SixMaxFlopIntervalCollection.MAX_ATTEMPTS));
    }

    @GetMapping("/questions/{index}")
    public ResponseEntity<SixMaxFlopIntervalCollection.Question> question(
            @PathVariable("index") int index,
            @RequestParam("collectionHash") String collectionHash) {
        requireCollection(collectionHash);
        return response(collection.question(index));
    }

    @PostMapping("/grade")
    public ResponseEntity<SixMaxFlopIntervalCollection.Feedback> grade(
            @Valid @RequestBody GradeRequest request) {
        requireCollection(request.collectionHash());
        var q = collection.question(request.questionIndex());
        if (!q.decision().reportHash().equals(request.reportHash()))
            throw new IllegalArgumentException("Question report pin differs");
        return response(collection.grade(q, request.action()));
    }

    @PostMapping("/review")
    public ResponseEntity<SixMaxFlopIntervalCollection.Review> review(
            @Valid @RequestBody ReviewRequest request) {
        requireCollection(request.collectionHash());
        return response(
                collection.review(
                        request.choices().stream()
                                .map(
                                        c ->
                                                new SixMaxFlopIntervalCollection.Choice(
                                                        c.questionIndex(),
                                                        c.reportHash(),
                                                        c.action()))
                                .toList()));
    }

    private void requireCollection(String hash) {
        if (!collection.collectionHash().equals(hash))
            throw new IllegalArgumentException("Collection changed; load its metadata again");
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
