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
    static final String IDENTIFIER_ADDED = "party.party.identifier-added";
    static final String IDENTIFIER_UPDATED = "party.party.identifier-updated";
    static final String IDENTIFIER_DELETED = "party.party.identifier-deleted";
    static final String ADDRESS_ADDED = "party.party.address-added";
    static final String ADDRESS_UPDATED = "party.party.address-updated";
    static final String ADDRESS_DELETED = "party.party.address-deleted";

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

    static EventEnvelope identifierAdded(Party party, PartyIdentifier identifier, Instant now) {
        long aggregateVersion = party.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", party.id());
        data.put("partyNumber", party.partyNumber());
        data.put("version", party.version());
        data.put("identifierId", identifier.id());
        data.put("identifierType", identifier.identifierType());
        data.put("identifierValue", identifier.identifierValue());
        if (identifier.issuingAuthority() != null) {
            data.put("issuingAuthority", identifier.issuingAuthority());
        }
        if (identifier.effectiveDate() != null) {
            data.put("effectiveDate", identifier.effectiveDate().toString());
        }
        data.put("createdBy", identifier.createdBy());
        data.put("createdAt", identifier.createdAt());

        return envelope(party, IDENTIFIER_ADDED, aggregateVersion, now, data);
    }

    static EventEnvelope addressAdded(Party party, PartyAddress address, Instant now) {
        long aggregateVersion = party.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", party.id());
        data.put("partyNumber", party.partyNumber());
        data.put("version", party.version());
        data.put("addressId", address.id());
        data.put("addressType", address.addressType());
        data.put("line1", address.line1());
        if (address.line2() != null) {
            data.put("line2", address.line2());
        }
        data.put("city", address.city());
        if (address.stateRegion() != null) {
            data.put("stateRegion", address.stateRegion());
        }
        data.put("postalCode", address.postalCode());
        data.put("country", address.country());
        if (address.effectiveDate() != null) {
            data.put("effectiveDate", address.effectiveDate().toString());
        }
        data.put("createdBy", address.createdBy());
        data.put("createdAt", address.createdAt());

        return envelope(party, ADDRESS_ADDED, aggregateVersion, now, data);
    }

    static EventEnvelope identifierUpdated(Party party, PartyIdentifier before, PartyIdentifier after,
                                           List<String> changedFields, Instant now) {
        long aggregateVersion = party.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", party.id());
        data.put("partyNumber", party.partyNumber());
        data.put("version", party.version());
        data.put("identifierId", after.id());
        data.put("changedFields", changedFields);
        data.put("from", identifierSnapshot(before));
        data.put("to", identifierSnapshot(after));

        return envelope(party, IDENTIFIER_UPDATED, aggregateVersion, now, data);
    }

    /**
     * Deletion carries the last stored snapshot of the removed identifier
     * because the row is gone after the transaction commits.
     */
    static EventEnvelope identifierDeleted(Party party, PartyIdentifier deleted, Instant now) {
        long aggregateVersion = party.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", party.id());
        data.put("partyNumber", party.partyNumber());
        data.put("version", party.version());
        data.put("identifierId", deleted.id());
        data.putAll(identifierSnapshot(deleted));
        data.put("createdBy", deleted.createdBy());
        data.put("createdAt", deleted.createdAt());

        return envelope(party, IDENTIFIER_DELETED, aggregateVersion, now, data);
    }

    static EventEnvelope addressUpdated(Party party, PartyAddress before, PartyAddress after,
                                        List<String> changedFields, Instant now) {
        long aggregateVersion = party.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", party.id());
        data.put("partyNumber", party.partyNumber());
        data.put("version", party.version());
        data.put("addressId", after.id());
        data.put("changedFields", changedFields);
        data.put("from", addressSnapshot(before));
        data.put("to", addressSnapshot(after));

        return envelope(party, ADDRESS_UPDATED, aggregateVersion, now, data);
    }

    /**
     * Deletion carries the last stored snapshot of the removed address because
     * the row is gone after the transaction commits.
     */
    static EventEnvelope addressDeleted(Party party, PartyAddress deleted, Instant now) {
        long aggregateVersion = party.version() + 1L;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("partyId", party.id());
        data.put("partyNumber", party.partyNumber());
        data.put("version", party.version());
        data.put("addressId", deleted.id());
        data.putAll(addressSnapshot(deleted));
        data.put("createdBy", deleted.createdBy());
        data.put("createdAt", deleted.createdAt());

        return envelope(party, ADDRESS_DELETED, aggregateVersion, now, data);
    }

    private static Map<String, Object> identifierSnapshot(PartyIdentifier identifier) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("identifierType", identifier.identifierType());
        snapshot.put("identifierValue", identifier.identifierValue());
        if (identifier.issuingAuthority() != null) {
            snapshot.put("issuingAuthority", identifier.issuingAuthority());
        }
        if (identifier.effectiveDate() != null) {
            snapshot.put("effectiveDate", identifier.effectiveDate().toString());
        }
        return snapshot;
    }

    private static Map<String, Object> addressSnapshot(PartyAddress address) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("addressType", address.addressType());
        snapshot.put("line1", address.line1());
        if (address.line2() != null) {
            snapshot.put("line2", address.line2());
        }
        snapshot.put("city", address.city());
        if (address.stateRegion() != null) {
            snapshot.put("stateRegion", address.stateRegion());
        }
        snapshot.put("postalCode", address.postalCode());
        snapshot.put("country", address.country());
        if (address.effectiveDate() != null) {
            snapshot.put("effectiveDate", address.effectiveDate().toString());
        }
        return snapshot;
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
