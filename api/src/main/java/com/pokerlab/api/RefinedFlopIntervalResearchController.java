package com.pokerlab.api;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.pokerlab.solver.SixMaxRefinedFlopTrainer;
import com.pokerlab.solver.SixMaxSuppliedFlopBettingGame;
import com.pokerlab.solver.SixMaxSuppliedRangePreflopGame;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Separate one-question interval protocol. Only canonical cached evidence reaches requests. */
@RestController
@RequestMapping("/api/v1/trainer/research/refined-flop-interval")
@ConditionalOnProperty(
        name = "pokerlab.trainer.refined-flop-interval-research-enabled",
        havingValue = "true")
public class RefinedFlopIntervalResearchController {
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
            int maximumAttempts,
            double maximumLossBb) {}

    public record GradeRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotBlank @Size(max = 32) String action) {}

    public record ReviewRequest(
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String reportHash,
            @NotNull @Size(max = SixMaxRefinedFlopTrainer.MAX_ATTEMPTS)
                    List<@NotBlank @Size(max = 32) String> actions) {}

    private static final ObjectMapper REQUEST_MAPPER =
            new ObjectMapper()
                    .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                    .enable(
                            DeserializationFeature.FAIL_ON_TRAILING_TOKENS,
                            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    private final Validator validator;
    private final SixMaxRefinedFlopTrainer trainer;

    public RefinedFlopIntervalResearchController(
            SixMaxRefinedFlopTrainer trainer, Validator validator) {
        this.trainer = trainer;
        this.validator = validator;
    }

    @GetMapping
    public ResponseEntity<Metadata> metadata() {
        var q = trainer.question();
        return response(
                new Metadata(
                        q.reportHash(),
                        q.publicationStatus(),
                        false,
                        q.selection(),
                        SixMaxSuppliedFlopBettingGame.MODEL,
                        SixMaxSuppliedFlopBettingGame.CHANCE,
                        q.evScope(),
                        q.reachUnits(),
                        SixMaxSuppliedRangePreflopGame.BELIEFS,
                        SixMaxSuppliedRangePreflopGame.HISTORY_REACH,
                        1,
                        2,
                        SixMaxRefinedFlopTrainer.MAX_ATTEMPTS,
                        q.maximumLossBb()));
    }

    @GetMapping("/question")
    public ResponseEntity<SixMaxRefinedFlopTrainer.Question> question() {
        return response(trainer.question());
    }

    @PostMapping(value = "/grade", consumes = "application/json")
    public ResponseEntity<SixMaxRefinedFlopTrainer.Feedback> grade(@RequestBody byte[] body) {
        var request = parse(body, GradeRequest.class);
        requireReport(request.reportHash());
        return response(trainer.grade(trainer.question(), request.action()));
    }

    @PostMapping(value = "/review", consumes = "application/json")
    public ResponseEntity<SixMaxRefinedFlopTrainer.Review> review(@RequestBody byte[] body) {
        var request = parse(body, ReviewRequest.class);
        requireReport(request.reportHash());
        return response(trainer.review(trainer.question(), request.actions()));
    }

    private <T> T parse(byte[] bytes, Class<T> type) {
        if (bytes.length > 32 * 1024)
            throw new IllegalArgumentException("Refined choice request exceeds byte cap");
        try {
            var request = REQUEST_MAPPER.readValue(bytes, type);
            if (request == null || !validator.validate(request).isEmpty())
                throw new IllegalArgumentException("Explicit valid pin and choices required");
            return request;
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Malformed refined choice request");
        }
    }

    private void requireReport(String hash) {
        if (!trainer.question().reportHash().equals(hash))
            throw new IllegalArgumentException(
                    "Qualification report changed; load the question again");
    }

    private static <T> ResponseEntity<T> response(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
