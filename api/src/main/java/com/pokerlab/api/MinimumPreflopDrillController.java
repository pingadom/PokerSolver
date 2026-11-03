package com.pokerlab.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.pokerlab.solver.SixMaxMinimumPreflopDrill;
import com.pokerlab.solver.SixMaxMinimumPreflopDrillLoader;
import com.pokerlab.solver.SixMaxRootMinimumFeedback;
import com.pokerlab.solver.SixMaxSuppliedFlopSizeMenuGame;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

/** Choices only: requests cannot supply solver inputs, EVs, classifications or saved reviews. */
@RestController
@RequestMapping("/api/v1/trainer/research/root-minimum-preflop-drill")
@ConditionalOnProperty(name = "pokerlab.trainer.root-minimum-preflop-enabled", havingValue = "true")
public class MinimumPreflopDrillController {
    public static final int MAX_REQUEST_BYTES = 32 * 1024;

    public record Metadata(
            String schemaVersion,
            String studyHash,
            String publicationStatus,
            boolean trainerAdmission,
            String model,
            String evScope,
            int tableSeats,
            int activePlayers,
            int availableQuestions,
            int cachedLegalGrades,
            int maximumAttempts,
            SixMaxMinimumPreflopDrillLoader.StartupBudget startupBudget) {}

    @Schema(name = "MinimumPreflopGradeRequest")
    public record GradeRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String studyHash,
            @NotNull @Min(0) @Max(0) Integer sourceCaseIndex,
            @NotBlank @Pattern(regexp = "fold|call|raise:9[.]0") String action) {}

    @Schema(name = "MinimumPreflopSubmittedChoice")
    public record SubmittedChoice(
            @NotNull @Min(0) @Max(0) Integer sourceCaseIndex,
            @NotBlank @Pattern(regexp = "fold|call|raise:9[.]0") String action) {}

    @Schema(name = "MinimumPreflopReviewRequest")
    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String studyHash,
            @NotNull @Size(max = SixMaxMinimumPreflopDrill.MAX_ATTEMPTS)
                    List<@NotNull @Valid SubmittedChoice> choices) {}

    private static final ObjectMapper MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                    .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final SixMaxMinimumPreflopDrill.Result drill;
    private final Validator validator;

    public MinimumPreflopDrillController(
            SixMaxMinimumPreflopDrill.Result drill, Validator validator) {
        this.drill = drill;
        this.validator = validator;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        return response(
                new Metadata(
                        SixMaxMinimumPreflopDrill.SCHEMA,
                        drill.snapshot().studyHash(),
                        SixMaxRootMinimumFeedback.STATUS,
                        false,
                        SixMaxSuppliedFlopSizeMenuGame.MODEL,
                        SixMaxMinimumPreflopDrill.EV_SCOPE,
                        6,
                        2,
                        drill.snapshot().questions().size(),
                        drill.snapshot().questions().stream().mapToInt(q -> q.moves().size()).sum(),
                        SixMaxMinimumPreflopDrill.MAX_ATTEMPTS,
                        SixMaxMinimumPreflopDrillLoader.StartupBudget.bounded()));
    }

    @GetMapping("/questions/{sourceCaseIndex}")
    public ResponseEntity<SixMaxMinimumPreflopDrill.Question> question(
            @PathVariable("sourceCaseIndex") int sourceCaseIndex,
            @RequestParam("studyHash") String studyHash) {
        pin(studyHash);
        var question =
                drill.snapshot().questions().stream()
                        .filter(q -> q.sourceCaseIndex() == sourceCaseIndex)
                        .findFirst()
                        .orElseThrow(
                                () -> new IllegalArgumentException("No owned qualified question"));
        return response(question);
    }

    @PostMapping(value = "/grade", consumes = "application/json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = GradeRequest.class)))
    public ResponseEntity<SixMaxMinimumPreflopDrill.Grade> grade(@RequestBody byte[] body) {
        var request = parse(body, GradeRequest.class);
        return response(
                drill.grade(request.studyHash(), request.sourceCaseIndex(), request.action()));
    }

    @PostMapping(value = "/review", consumes = "application/json")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(
            required = true,
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ReviewRequest.class)))
    public ResponseEntity<SixMaxMinimumPreflopDrill.Review> review(@RequestBody byte[] body) {
        return response(review(parse(body, ReviewRequest.class)));
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
                                                    SixMaxMinimumPreflopDrill.Review.class)))
    public ResponseEntity<String> export(@RequestBody byte[] body) throws Exception {
        var canonical = review(parse(body, ReviewRequest.class));
        String text =
                MAPPER.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(canonical)
                                .replace("\r\n", "\n")
                        + "\n";
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(new MediaType("application", "json", StandardCharsets.UTF_8))
                .header(
                        "Content-Disposition",
                        "attachment; filename=\"pokerlab-root-minimum-preflop-review-"
                                + drill.snapshot().studyHash().substring(0, 12)
                                + ".json\"")
                .body(text);
    }

    private SixMaxMinimumPreflopDrill.Review review(ReviewRequest request) {
        return drill.review(
                request.studyHash(),
                request.choices().stream()
                        .map(
                                c ->
                                        new SixMaxMinimumPreflopDrill.Attempt(
                                                c.sourceCaseIndex(), c.action()))
                        .toList());
    }

    private void pin(String hash) {
        if (!drill.snapshot().studyHash().equals(hash))
            throw new IllegalArgumentException("Study changed; load metadata again");
    }

    private <T> T parse(byte[] bytes, Class<T> type) {
        if (bytes.length > MAX_REQUEST_BYTES)
            throw new IllegalArgumentException("minimum preflop choice request exceeds byte cap");
        try {
            var request = MAPPER.readValue(bytes, type);
            if (request == null || !validator.validate(request).isEmpty())
                throw new IllegalArgumentException(
                        "Explicit valid study pin, index and choices required");
            return request;
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Malformed minimum preflop choice request");
        }
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
