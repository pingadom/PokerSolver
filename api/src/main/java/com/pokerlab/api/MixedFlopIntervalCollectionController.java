package com.pokerlab.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.pokerlab.solver.SixMaxMixedFlopIntervalCollection;
import com.pokerlab.solver.SixMaxSuppliedFlopActionBounds;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame;
import com.pokerlab.solver.SixMaxSuppliedFlopFeedback;
import com.pokerlab.solver.SixMaxSuppliedRangePreflopGame;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Separate ordered-collection protocol. Client choices cannot supply questions, EVs or grades. */
@RestController
@RequestMapping("/api/v1/trainer/research/flop-interval-collection-v2")
@ConditionalOnProperty(
        name = "pokerlab.trainer.mixed-flop-interval-collection-enabled",
        havingValue = "true")
public class MixedFlopIntervalCollectionController {
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
            List<SixMaxMixedFlopIntervalCollection.Kind> questionKinds,
            int availableQuestions,
            int activePlayers,
            int maximumAttempts) {
        public Metadata {
            questionKinds = List.copyOf(questionKinds);
        }
    }

    @Schema(name = "MixedFlopV2GradeRequest")
    public record GradeRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String collectionHash,
            @NotNull @Min(0) @Max(SixMaxMixedFlopIntervalCollection.MAX_QUESTIONS - 1)
                    Integer questionIndex,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    @Schema(name = "MixedFlopV2SubmittedChoice")
    public record SubmittedChoice(
            @NotNull @Min(0) @Max(SixMaxMixedFlopIntervalCollection.MAX_QUESTIONS - 1)
                    Integer questionIndex,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    @Schema(name = "MixedFlopV2ReviewRequest")
    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String collectionHash,
            @NotNull @Size(max = SixMaxMixedFlopIntervalCollection.MAX_ATTEMPTS)
                    List<@NotNull @Valid SubmittedChoice> choices) {}

    private static final ObjectMapper REQUEST_MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    private final Validator validator;
    private final SixMaxMixedFlopIntervalCollection collection;

    public MixedFlopIntervalCollectionController(
            SixMaxMixedFlopIntervalCollection collection, Validator validator) {
        this.collection = collection;
        this.validator = validator;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        return response(
                new Metadata(
                        collection.collectionHash(),
                        SixMaxMixedFlopIntervalCollection.STATUS,
                        false,
                        SixMaxMixedFlopIntervalCollection.SELECTION,
                        SixMaxSuppliedFlopBettingGame.MODEL,
                        SixMaxSuppliedFlopBettingGame.CHANCE,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                        SixMaxSuppliedRangePreflopGame.BELIEFS,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        collection.questions().stream().map(q -> q.decision().kind()).toList(),
                        collection.questions().size(),
                        2,
                        SixMaxMixedFlopIntervalCollection.MAX_ATTEMPTS));
    }

    @GetMapping("/questions/{index}")
    public ResponseEntity<SixMaxMixedFlopIntervalCollection.Question> question(
            @PathVariable("index") int index,
            @RequestParam("collectionHash") String collectionHash) {
        requireCollection(collectionHash);
        return response(collection.question(index));
    }

    @PostMapping(value = "/grade", consumes = "application/json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GradeRequest.class)))
    public ResponseEntity<SixMaxMixedFlopIntervalCollection.Feedback> grade(
            @RequestBody byte[] body) {
        var request = parse(body, GradeRequest.class);
        requireCollection(request.collectionHash());
        var q = collection.question(request.questionIndex());
        if (!q.decision().reportHash().equals(request.reportHash()))
            throw new IllegalArgumentException("Question report pin differs");
        return response(collection.grade(q, request.action()));
    }

    @PostMapping(value = "/review", consumes = "application/json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewRequest.class)))
    public ResponseEntity<SixMaxMixedFlopIntervalCollection.Review> review(
            @RequestBody byte[] body) {
        var request = parse(body, ReviewRequest.class);
        requireCollection(request.collectionHash());
        return response(
                collection.review(
                        request.choices().stream()
                                .map(
                                        c ->
                                                new SixMaxMixedFlopIntervalCollection.Choice(
                                                        c.questionIndex(),
                                                        c.reportHash(),
                                                        c.action()))
                                .toList()));
    }

    @PostMapping(
            value = "/review/export",
            consumes = "application/json",
            produces = "application/json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewRequest.class)))
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema =
                                    @Schema(
                                            implementation =
                                                    SixMaxMixedFlopIntervalCollection.Review
                                                            .class)))
    public ResponseEntity<String> export(@RequestBody byte[] body) throws Exception {
        var request = parse(body, ReviewRequest.class);
        requireCollection(request.collectionHash());
        var text =
                collection.reviewJson(
                        request.choices().stream()
                                .map(
                                        c ->
                                                new SixMaxMixedFlopIntervalCollection.Choice(
                                                        c.questionIndex(),
                                                        c.reportHash(),
                                                        c.action()))
                                .toList());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(
                        new org.springframework.http.MediaType(
                                "application", "json", java.nio.charset.StandardCharsets.UTF_8))
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"pokerlab-mixed-flop-review-"
                                + collection.collectionHash().substring(0, 12)
                                + ".json\"")
                .body(text);
    }

    private <T> T parse(byte[] bytes, Class<T> type) {
        if (bytes.length > 32 * 1024)
            throw new IllegalArgumentException("Mixed collection choice request exceeds byte cap");
        try {
            var request = REQUEST_MAPPER.readValue(bytes, type);
            if (request == null || !validator.validate(request).isEmpty())
                throw new IllegalArgumentException(
                        "Explicit valid pin, index and choices required");
            return request;
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Malformed Mixed collection choice request");
        }
    }

    private void requireCollection(String hash) {
        if (!collection.collectionHash().equals(hash))
            throw new IllegalArgumentException("Collection changed; load its metadata again");
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
