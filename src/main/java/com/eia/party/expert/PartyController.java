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
    private final PartyService service;

    public PartyController(PartyRepository repository, PartyService service) {
        this.repository = repository;
        this.service = service;
    }

    @GetMapping
    public List<Party> list(@RequestParam UUID tenantId) {
        return repository.findAll(tenantId);
    }

    @GetMapping("/{id}")
    public Party get(@RequestParam UUID tenantId, @PathVariable UUID id) {
        return repository.findById(tenantId, id).orElseThrow(() -> new PartyNotFoundException(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Party create(@Valid @RequestBody CreatePartyRequest request) {
        return service.create(request);
    }

    @PatchMapping("/{id}/status")
    public Party updateStatus(
            @RequestParam UUID tenantId,
            @PathVariable UUID id,
            @Valid @RequestBody UpdatePartyStatusRequest request) {
        return service.updateStatus(tenantId, id, request);
    }
}
