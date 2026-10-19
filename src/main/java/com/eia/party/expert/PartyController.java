package com.eia.party.expert;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/parties")
public class PartyController {
    private final PartyRepository repository;

    public PartyController(PartyRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<Party> list(@RequestParam UUID tenantId) {
        return repository.findAll(tenantId);
    }

    @GetMapping("/{id}")
    public Party get(@RequestParam UUID tenantId, @PathVariable UUID id) {
        return repository.findById(tenantId, id).orElseThrow(() -> new NotFoundException(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Party create(@Valid @RequestBody CreatePartyRequest request) {
        return repository.create(request);
    }

    static class NotFoundException extends RuntimeException {
        NotFoundException(UUID id) { super("Party not found: " + id); }
    }
}
