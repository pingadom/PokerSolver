package com.pokerlab.api;

import com.pokerlab.solver.PreflopTrainer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Explicitly opt-in HTTP boundary for validation-only drills. It never runs the solver. */
@RestController
@RequestMapping("/api/v1/trainer/research")
@ConditionalOnProperty(name = "pokerlab.trainer.research-enabled", havingValue = "true")
public class ResearchTrainerController {
    public record QuestionResponse(String seed, PreflopTrainer.Question question) {}

    public record GradeRequest(
            @NotBlank String seed,
            @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String spotHash,
            @NotNull PreflopTrainer.Action action) {}

    private final PreflopTrainer trainer;

    public ResearchTrainerController(PreflopTrainer trainer) {
        this.trainer = trainer;
    }

    @GetMapping("/questions/{seed}")
    public ResponseEntity<QuestionResponse> question(@PathVariable("seed") String seed) {
        PreflopTrainer.Question question = trainer.question(parseSeed(seed));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new QuestionResponse(seed, question));
    }

    @PostMapping("/grade")
    public ResponseEntity<PreflopTrainer.Feedback> grade(@Valid @RequestBody GradeRequest request) {
        PreflopTrainer.Question question = trainer.question(parseSeed(request.seed()));
        if (!question.spotHash().equals(request.spotHash()))
            throw new IllegalArgumentException("Question spot version has changed");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(trainer.grade(question, request.action()));
    }

    private static long parseSeed(String seed) {
        try {
            return Long.parseLong(seed);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Seed must be a signed 64-bit integer", exception);
        }
    }
}
