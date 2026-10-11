package com.pokerlab.api;

import com.pokerlab.solver.SixMaxSuppliedIntervalTrainer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** Separate one-question interval protocol. Only canonical cached evidence reaches requests. */
@RestController
@RequestMapping("/api/v1/trainer/research/interval")
@ConditionalOnProperty(name = "pokerlab.trainer.interval-research-enabled", havingValue = "true")
public class IntervalResearchController {
    public record Metadata(
            String reportHash,
            String publicationStatus,
            boolean trainerAdmission,
            String selection,
            String model,
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
            @NotNull @Size(max = SixMaxSuppliedIntervalTrainer.MAX_ATTEMPTS)
                    List<@NotBlank @Size(max = 32) String> actions) {}

    private final SixMaxSuppliedIntervalTrainer trainer;

    public IntervalResearchController(SixMaxSuppliedIntervalTrainer trainer) {
        this.trainer = trainer;
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
                        q.model(),
                        q.beliefs(),
                        q.sourceHistoryReachStatus(),
                        1,
                        2,
                        SixMaxSuppliedIntervalTrainer.MAX_ATTEMPTS,
                        q.maximumLossBb()));
    }

    @GetMapping("/question")
    public ResponseEntity<SixMaxSuppliedIntervalTrainer.Question> question() {
        return response(trainer.question());
    }

    @PostMapping("/grade")
    public ResponseEntity<SixMaxSuppliedIntervalTrainer.Feedback> grade(
            @Valid @RequestBody GradeRequest request) {
        requireReport(request.reportHash());
        return response(trainer.grade(trainer.question(), request.action()));
    }

    @PostMapping("/review")
    public ResponseEntity<SixMaxSuppliedIntervalTrainer.Review> review(
            @Valid @RequestBody ReviewRequest request) {
        requireReport(request.reportHash());
        return response(trainer.review(trainer.question(), request.actions()));
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
