package com.pokerlab.api;

import com.pokerlab.solver.SixMaxSuppliedIntervalCollection;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Separate ordered-collection protocol. Client choices cannot supply questions, EVs or grades. */
@RestController
@RequestMapping("/api/v1/trainer/research/interval-collection")
@ConditionalOnProperty(name = "pokerlab.trainer.interval-collection-enabled", havingValue = "true")
public class IntervalCollectionController {
    public record Metadata(
            String collectionHash,
            String publicationStatus,
            boolean trainerAdmission,
            String selection,
            int availableQuestions,
            int activePlayers,
            int maximumAttempts) {}

    public record GradeRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String collectionHash,
            @NotNull @Min(0) @Max(SixMaxSuppliedIntervalCollection.MAX_QUESTIONS - 1)
                    Integer questionIndex,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    public record SubmittedChoice(
            @NotNull @Min(0) @Max(SixMaxSuppliedIntervalCollection.MAX_QUESTIONS - 1)
                    Integer questionIndex,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String collectionHash,
            @NotNull @Size(max = SixMaxSuppliedIntervalCollection.MAX_ATTEMPTS)
                    List<@NotNull @Valid SubmittedChoice> choices) {}

    private final SixMaxSuppliedIntervalCollection collection;

    public IntervalCollectionController(SixMaxSuppliedIntervalCollection collection) {
        this.collection = collection;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        return response(
                new Metadata(
                        collection.collectionHash(),
                        SixMaxSuppliedIntervalCollection.STATUS,
                        false,
                        SixMaxSuppliedIntervalCollection.SELECTION,
                        collection.questions().size(),
                        2,
                        SixMaxSuppliedIntervalCollection.MAX_ATTEMPTS));
    }

    @GetMapping("/questions/{index}")
    public ResponseEntity<SixMaxSuppliedIntervalCollection.Question> question(
            @PathVariable("index") int index,
            @RequestParam("collectionHash") String collectionHash) {
        requireCollection(collectionHash);
        return response(collection.question(index));
    }

    @PostMapping("/grade")
    public ResponseEntity<SixMaxSuppliedIntervalCollection.Feedback> grade(
            @Valid @RequestBody GradeRequest request) {
        requireCollection(request.collectionHash());
        var q = collection.question(request.questionIndex());
        if (!q.decision().reportHash().equals(request.reportHash()))
            throw new IllegalArgumentException("Question report pin differs");
        return response(collection.grade(q, request.action()));
    }

    @PostMapping("/review")
    public ResponseEntity<SixMaxSuppliedIntervalCollection.Review> review(
            @Valid @RequestBody ReviewRequest request) {
        requireCollection(request.collectionHash());
        return response(
                collection.review(
                        request.choices().stream()
                                .map(
                                        c ->
                                                new SixMaxSuppliedIntervalCollection.Choice(
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
