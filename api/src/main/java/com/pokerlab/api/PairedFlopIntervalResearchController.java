package com.pokerlab.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.pokerlab.solver.SixMaxPairedFlopTrainer;
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

/** Cached BTN protocol; request bodies can submit only pins, indices and action choices. */
@RestController
@RequestMapping("/api/v1/trainer/research/paired-flop-interval")
@ConditionalOnProperty(
        name = "pokerlab.trainer.paired-flop-interval-research-enabled",
        havingValue = "true")
public class PairedFlopIntervalResearchController {
    public record Metadata(
            String reportHash,
            String publicationStatus,
            boolean trainerAdmission,
            String selection,
            String model,
            String chanceModel,
            String evScope,
            String reachUnits,
            String beliefs,
            String sourceHistoryReachStatus,
            int availableQuestions,
            int activePlayers,
            int maximumAttempts) {}

    @Schema(name = "PairedFlopIntervalGradeRequest")
    public record GradeRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotNull @Min(0) @Max(SixMaxPairedFlopTrainer.QUESTION_COUNT - 1) Integer questionIndex,
            @NotBlank @Size(max = 32) String action) {}

    @Schema(name = "PairedFlopIntervalChoice")
    public record SubmittedChoice(
            @NotNull @Min(0) @Max(SixMaxPairedFlopTrainer.QUESTION_COUNT - 1) Integer questionIndex,
            @NotBlank @Size(max = 32) String action) {}

    @Schema(name = "PairedFlopIntervalReviewRequest")
    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotNull @Size(max = SixMaxPairedFlopTrainer.MAX_ATTEMPTS)
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
    private final SixMaxPairedFlopTrainer trainer;

    public PairedFlopIntervalResearchController(
            SixMaxPairedFlopTrainer trainer, Validator validator) {
        this.trainer = trainer;
        this.validator = validator;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        return response(
                new Metadata(
                        trainer.reportHash(),
                        SixMaxPairedFlopTrainer.STATUS,
                        false,
                        SixMaxPairedFlopTrainer.SELECTION,
                        SixMaxSuppliedFlopBettingGame.MODEL,
                        SixMaxSuppliedFlopBettingGame.CHANCE,
                        SixMaxSuppliedFlopFeedback.EV_SCOPE,
                        SixMaxSuppliedFlopActionBounds.REACH_UNITS,
                        SixMaxSuppliedRangePreflopGame.BELIEFS,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        trainer.questions().size(),
                        2,
                        SixMaxPairedFlopTrainer.MAX_ATTEMPTS));
    }

    @GetMapping("/questions/{index}")
    public ResponseEntity<SixMaxPairedFlopTrainer.Question> question(
            @PathVariable("index") int index, @RequestParam("reportHash") String reportHash) {
        requireReport(reportHash);
        return response(trainer.question(index));
    }

    @PostMapping(value = "/grade", consumes = "application/json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GradeRequest.class)))
    public ResponseEntity<SixMaxPairedFlopTrainer.Feedback> grade(@RequestBody byte[] body) {
        var request = parse(body, GradeRequest.class);
        requireReport(request.reportHash());
        var q = trainer.question(request.questionIndex());
        return response(trainer.grade(q, request.action()));
    }

    @PostMapping(value = "/review", consumes = "application/json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewRequest.class)))
    public ResponseEntity<SixMaxPairedFlopTrainer.Review> review(@RequestBody byte[] body) {
        var request = parse(body, ReviewRequest.class);
        requireReport(request.reportHash());
        return response(
                trainer.review(
                        request.choices().stream()
                                .map(
                                        c ->
                                                new SixMaxPairedFlopTrainer.Choice(
                                                        c.questionIndex(), c.action()))
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
    public ResponseEntity<String> export(@RequestBody byte[] body) throws Exception {
        var request = parse(body, ReviewRequest.class);
        requireReport(request.reportHash());
        var text =
                trainer.reviewJson(
                        request.choices().stream()
                                .map(
                                        c ->
                                                new SixMaxPairedFlopTrainer.Choice(
                                                        c.questionIndex(), c.action()))
                                .toList());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(
                        new org.springframework.http.MediaType(
                                "application", "json", java.nio.charset.StandardCharsets.UTF_8))
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"pokerlab-paired-flop-review-"
                                + trainer.reportHash().substring(0, 12)
                                + ".json\"")
                .body(text);
    }

    private <T> T parse(byte[] bytes, Class<T> type) {
        if (bytes.length > 32 * 1024)
            throw new IllegalArgumentException("BTN choice request exceeds byte cap");
        try {
            var request = REQUEST_MAPPER.readValue(bytes, type);
            if (request == null || !validator.validate(request).isEmpty())
                throw new IllegalArgumentException(
                        "Explicit valid pin, index and choices required");
            return request;
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Malformed BTN choice request");
        }
    }

    private void requireReport(String hash) {
        if (!trainer.reportHash().equals(hash))
            throw new IllegalArgumentException("Report changed; load its metadata again");
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
