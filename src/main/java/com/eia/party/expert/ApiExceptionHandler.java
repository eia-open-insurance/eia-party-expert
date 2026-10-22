package com.eia.party.expert;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PartyNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(PartyNotFoundException ex) {
        return Map.of("code", "NOT_FOUND", "message", ex.getMessage());
    }

    @ExceptionHandler(PartyChildNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> childNotFound(PartyChildNotFoundException ex) {
        return Map.of("code", "NOT_FOUND", "message", ex.getMessage());
    }

    @ExceptionHandler(PartyIdentifierConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> conflict(PartyIdentifierConflictException ex) {
        return Map.of("code", "CONFLICT", "message", ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> badRequest(IllegalArgumentException ex) {
        return Map.of("code", "VALIDATION_ERROR", "message", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidBody(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .findFirst()
                .orElse("Request validation failed");
        return Map.of("code", "VALIDATION_ERROR", "message", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> unreadableBody(HttpMessageNotReadableException ex) {
        return Map.of("code", "VALIDATION_ERROR", "message", "Malformed request body");
    }
}
