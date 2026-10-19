package com.eia.party.expert;

import com.eia.platform.libs.outbox.OutboxEvent;
import com.eia.platform.libs.outbox.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Party writes. The state change and its outbox row are committed in one
 * transaction, so an event is never lost and a rollback leaves neither the
 * party mutation nor the event behind.
 */
@Service
public class PartyService {
    private static final Set<String> STATUSES = Set.of(
            "PROSPECT", "ACTIVE", "INACTIVE", "DECEASED",
            "DISSOLVED", "MERGED", "RESTRICTED", "BLOCKED");

    private final PartyRepository parties;
    private final OutboxRepository outbox;

    public PartyService(PartyRepository parties, OutboxRepository outbox) {
        this.parties = parties;
        this.outbox = outbox;
    }

    @Transactional
    public Party create(CreatePartyRequest request) {
        Party party = parties.create(request);
        Instant now = Instant.now();
        outbox.insert(OutboxEvent.fromEnvelope(PartyEvents.created(party, now)));
        return party;
    }

    @Transactional
    public Party updateStatus(UUID tenantId, UUID id, UpdatePartyStatusRequest request) {
        Party before = parties.findById(tenantId, id)
                .orElseThrow(() -> new PartyNotFoundException(id));
        String status = normalizeStatus(request.status());
        if (before.status().equals(status)) {
            return before;
        }

        Party after = parties.updateStatus(tenantId, id, status)
                .orElseThrow(() -> new PartyNotFoundException(id));
        Instant now = Instant.now();
        String reason = request.reason() == null || request.reason().isBlank()
                ? null
                : request.reason().trim();
        outbox.insert(OutboxEvent.fromEnvelope(PartyEvents.statusUpdated(before, after, reason, now)));
        return after;
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported party status: " + status);
        }
        return normalized;
    }
}
