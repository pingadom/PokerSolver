package com.pokerlab.api;

import com.pokerlab.shared.SimulationNotFoundException;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiErrors {
    public record ErrorBody(String code, String message) {}

    @ExceptionHandler(SimulationNotFoundException.class)
    ResponseEntity<ErrorBody> missing(SimulationNotFoundException error) {
        return ResponseEntity.status(404).body(new ErrorBody("NOT_FOUND", error.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> missingResource(NoResourceFoundException error) {
        return ResponseEntity.status(404)
                .body(new ErrorBody("NOT_FOUND", "Resource was not found"));
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        MethodArgumentTypeMismatchException.class,
        HttpMessageNotReadableException.class
    })
    ResponseEntity<ErrorBody> invalid(Exception error) {
        return ResponseEntity.badRequest()
                .body(
                        new ErrorBody(
                                "INVALID_REQUEST",
                                error instanceof IllegalArgumentException
                                        ? error.getMessage()
                                        : "Malformed request or parameter"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> validation(MethodArgumentNotValidException error) {
        String message =
                error.getBindingResult().getFieldErrors().stream()
                        .map(e -> e.getField() + ": " + e.getDefaultMessage())
                        .sorted()
                        .reduce((a, b) -> a + "; " + b)
                        .orElse("Invalid request");
        return ResponseEntity.badRequest().body(new ErrorBody("INVALID_REQUEST", message));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> internal(Exception error) {
        LoggerFactory.getLogger(ApiErrors.class).error("Request failed", error);
        return ResponseEntity.internalServerError()
                .body(new ErrorBody("INTERNAL_ERROR", "The request could not be completed"));
    }
}
