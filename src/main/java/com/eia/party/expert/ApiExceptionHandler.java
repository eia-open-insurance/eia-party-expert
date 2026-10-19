package com.eia.party.expert;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(PartyController.NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, String> notFound(PartyController.NotFoundException ex) {
        return Map.of("code", "NOT_FOUND", "message", ex.getMessage());
    }
}
