package com.eia.party.expert;

import com.eia.platform.libs.events.Book;
import com.eia.platform.libs.events.EventActor;
import com.eia.platform.libs.events.EventEnvelope;
import com.eia.platform.libs.events.EventIds;
import com.eia.platform.libs.events.EventMetadata;
import com.eia.platform.libs.events.EventSubject;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Event factory for the Party vertical. Event names are from SPEC 11.1.9 and
 * the canonical catalog in {@code eia-contracts/asyncapi/event-catalog.yaml}.
 *
 * <p>Party is shared master data and the current Party API has no book input;
 * the canonical envelope requires a book, so these master-data events are
 * stamped DIRECT until role-level book scoping is implemented.</p>
 */
final class PartyEvents {
    static final String CREATED = "party.party.created";
    static final String UPDATED = "party.party.updated";

    private static final Book EVENT_BOOK = Book.DIRECT;
    private static final String VERTICAL = "party";
    private static final String AGGREGATE_TYPE = "Party";
    private static final String SOURCE_BUILD = "eia-party-expert@0.1.0-SNAPSHOT";

    private PartyEvents() {
    }

    static EventEnvelope created(Party party, Instant now) {
        long aggregateVersion = party.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", party.id());
        data.put("partyNumber", party.partyNumber());
        data.put("partyType", party.partyType());
        data.put("status", party.status());
        data.put("roles", List.of());

        return envelope(party, CREATED, aggregateVersion, now, data);
    }

    static EventEnvelope statusUpdated(Party before, Party after, String reason, Instant now) {
        long aggregateVersion = after.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", after.id());
        data.put("partyNumber", after.partyNumber());
        data.put("changedFields", List.of("status"));
        data.put("version", after.version());
        data.put("from", before.status());
        data.put("to", after.status());
        if (reason != null && !reason.isBlank()) {
            data.put("reason", reason);
        }

        return envelope(after, UPDATED, aggregateVersion, now, data);
    }

    private static EventEnvelope envelope(
            Party party, String eventType, long aggregateVersion, Instant now, Map<String, Object> data) {
        // Party rows start at version 0 for API compatibility. The canonical
        // envelope requires aggregateVersion >= 1 and monotonicity, so the event
        // version is the row version plus one (create -> 1, first update -> 2).
        UUID eventId = EventIds.deterministic(
                party.tenantId(), AGGREGATE_TYPE, party.id(), aggregateVersion, eventType);
        return EventEnvelope.builder()
                .eventId(eventId)
                .eventType(eventType)
                .eventVersion(1)
                .occurredAt(now)
                .recordedAt(now)
                .tenantId(party.tenantId())
                .book(EVENT_BOOK)
                .vertical(VERTICAL)
                .aggregateType(AGGREGATE_TYPE)
                .aggregateId(party.id())
                .aggregateVersion(aggregateVersion)
                .sequence(0)
                .actor(EventActor.system("eia-party-expert"))
                .subject(EventSubject.of(VERTICAL, "party", party.id(), party.partyNumber()))
                .links(List.of())
                .data(data)
                .metadata(EventMetadata.v1(SOURCE_BUILD))
                .build();
    }
}
