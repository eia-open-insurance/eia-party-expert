package com.eia.party.expert;

import com.eia.platform.libs.outbox.OutboxEvent;
import com.eia.platform.libs.outbox.OutboxRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Read and write operations for party identifiers and addresses.
 *
 * <p>Identifiers and addresses are part of the Party aggregate. Adding,
 * updating, or deleting one bumps the party version, and the fact and its
 * outbox row commit in the same transaction as the child write, so a rollback
 * leaves neither behind.</p>
 */
@Service
public class PartyReferenceDataService {
    static final Set<String> IDENTIFIER_TYPES = Set.of(
            "TAX_ID", "REGISTRATION", "LICENSE",
            "TIN", "EIN", "SSN", "ITIN", "DUNS", "LEI", "PASSPORT", "DRIVER_LICENSE",
            "STATE_ID", "MEDICARE_ID", "MEDICAID_ID", "NPI", "BAR_NUMBER",
            "ACORD_PARTY_ID", "ISO_PARTY_ID", "TRUST_CLAIM_NUMBER", "BANKRUPTCY_CASE_NUMBER", "OTHER");
    static final Set<String> ADDRESS_TYPES = Set.of(
            "PRIMARY", "LEGAL", "PHYSICAL", "MAILING", "BILLING", "CLAIM_SITE", "EXPOSURE_SITE",
            "EMPLOYER", "PROVIDER", "BANK", "OTHER");
    private static final String CREATED_BY = "system";

    private final PartyRepository parties;
    private final PartyIdentifierRepository identifiers;
    private final PartyAddressRepository addresses;
    private final OutboxRepository outbox;

    public PartyReferenceDataService(PartyRepository parties,
                                     PartyIdentifierRepository identifiers,
                                     PartyAddressRepository addresses,
                                     OutboxRepository outbox) {
        this.parties = parties;
        this.identifiers = identifiers;
        this.addresses = addresses;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<PartyIdentifier> identifiers(UUID tenantId, UUID partyId) {
        requireParty(tenantId, partyId);
        return identifiers.findAll(tenantId, partyId);
    }

    @Transactional(readOnly = true)
    public List<PartyAddress> addresses(UUID tenantId, UUID partyId) {
        requireParty(tenantId, partyId);
        return addresses.findAll(tenantId, partyId);
    }

    @Transactional
    public PartyIdentifier addIdentifier(UUID tenantId, UUID partyId,
                                         CreatePartyIdentifierRequest request) {
        requireParty(tenantId, partyId);
        String identifierType = normalizeType(
                request.identifierType(), IDENTIFIER_TYPES, "identifierType");
        String identifierValue = required(request.identifierValue(), "identifierValue", 128);
        String issuingAuthority = optional(request.issuingAuthority(), "issuingAuthority", 128);

        if (identifiers.exists(tenantId, partyId, identifierType, identifierValue)) {
            throw new PartyIdentifierConflictException(identifierType);
        }

        Instant now = Instant.now();
        PartyIdentifier identifier = new PartyIdentifier(
                UUID.randomUUID(), tenantId, partyId, identifierType, identifierValue,
                issuingAuthority, request.effectiveDate(), now, CREATED_BY);
        try {
            identifiers.insert(identifier);
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request won the unique key after the pre-check.
            throw new PartyIdentifierConflictException(identifierType);
        }

        Party updated = parties.bumpVersion(tenantId, partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        outbox.insert(OutboxEvent.fromEnvelope(PartyEvents.identifierAdded(updated, identifier, now)));
        return identifier;
    }

    @Transactional
    public PartyAddress addAddress(UUID tenantId, UUID partyId,
                                   CreatePartyAddressRequest request) {
        requireParty(tenantId, partyId);
        String addressType = normalizeType(request.addressType(), ADDRESS_TYPES, "addressType");
        String line1 = required(request.line1(), "line1", 128);
        String line2 = optional(request.line2(), "line2", 128);
        String city = required(request.city(), "city", 64);
        String stateRegion = optional(request.stateRegion(), "stateRegion", 64);
        String postalCode = required(request.postalCode(), "postalCode", 16);
        String country = country(request.country());

        Instant now = Instant.now();
        PartyAddress address = new PartyAddress(
                UUID.randomUUID(), tenantId, partyId, addressType, line1, line2, city,
                stateRegion, postalCode, country, request.effectiveDate(), now, CREATED_BY);
        addresses.insert(address);

        Party updated = parties.bumpVersion(tenantId, partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        outbox.insert(OutboxEvent.fromEnvelope(PartyEvents.addressAdded(updated, address, now)));
        return address;
    }

    /**
     * Replaces the mutable fields of one identifier. The child is looked up by
     * tenant, party, and identifier id, so an identifier of another party or
     * tenant is a 404. A change that would duplicate the unique
     * {@code (tenant, party, identifierType, identifierValue)} key is a 409.
     * An update with no field change is a no-op: no version bump and no event.
     */
    @Transactional
    public PartyIdentifier updateIdentifier(UUID tenantId, UUID partyId, UUID identifierId,
                                            UpdatePartyIdentifierRequest request) {
        requireParty(tenantId, partyId);
        PartyIdentifier before = identifiers.findById(tenantId, partyId, identifierId)
                .orElseThrow(() -> new PartyChildNotFoundException("Identifier", identifierId));

        String identifierType = normalizeType(
                request.identifierType(), IDENTIFIER_TYPES, "identifierType");
        String identifierValue = required(request.identifierValue(), "identifierValue", 128);
        String issuingAuthority = optional(request.issuingAuthority(), "issuingAuthority", 128);

        List<String> changedFields = new ArrayList<>();
        if (!before.identifierType().equals(identifierType)) {
            changedFields.add("identifierType");
        }
        if (!before.identifierValue().equals(identifierValue)) {
            changedFields.add("identifierValue");
        }
        if (!Objects.equals(before.issuingAuthority(), issuingAuthority)) {
            changedFields.add("issuingAuthority");
        }
        if (!Objects.equals(before.effectiveDate(), request.effectiveDate())) {
            changedFields.add("effectiveDate");
        }
        if (changedFields.isEmpty()) {
            return before;
        }

        boolean keyChanged = !before.identifierType().equals(identifierType)
                || !before.identifierValue().equals(identifierValue);
        if (keyChanged && identifiers.existsExcluding(
                tenantId, partyId, identifierType, identifierValue, identifierId)) {
            throw new PartyIdentifierConflictException(identifierType);
        }

        PartyIdentifier after = new PartyIdentifier(
                identifierId, tenantId, partyId, identifierType, identifierValue,
                issuingAuthority, request.effectiveDate(), before.createdAt(), before.createdBy());
        try {
            if (identifiers.update(after) == 0) {
                throw new PartyChildNotFoundException("Identifier", identifierId);
            }
        } catch (DataIntegrityViolationException ex) {
            // A concurrent request won the unique key after the pre-check.
            throw new PartyIdentifierConflictException(identifierType);
        }

        Party updated = parties.bumpVersion(tenantId, partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        outbox.insert(OutboxEvent.fromEnvelope(
                PartyEvents.identifierUpdated(updated, before, after, changedFields, Instant.now())));
        return after;
    }

    /** Hard-deletes one identifier and records its last snapshot in the event. */
    @Transactional
    public void deleteIdentifier(UUID tenantId, UUID partyId, UUID identifierId) {
        requireParty(tenantId, partyId);
        PartyIdentifier deleted = identifiers.findById(tenantId, partyId, identifierId)
                .orElseThrow(() -> new PartyChildNotFoundException("Identifier", identifierId));
        if (identifiers.delete(tenantId, partyId, identifierId) == 0) {
            throw new PartyChildNotFoundException("Identifier", identifierId);
        }

        Party updated = parties.bumpVersion(tenantId, partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        outbox.insert(OutboxEvent.fromEnvelope(
                PartyEvents.identifierDeleted(updated, deleted, Instant.now())));
    }

    /**
     * Replaces the mutable fields of one address. The child is looked up by
     * tenant, party, and address id, so an address of another party or tenant
     * is a 404. An update with no field change is a no-op: no version bump and
     * no event.
     */
    @Transactional
    public PartyAddress updateAddress(UUID tenantId, UUID partyId, UUID addressId,
                                      UpdatePartyAddressRequest request) {
        requireParty(tenantId, partyId);
        PartyAddress before = addresses.findById(tenantId, partyId, addressId)
                .orElseThrow(() -> new PartyChildNotFoundException("Address", addressId));

        String addressType = normalizeType(request.addressType(), ADDRESS_TYPES, "addressType");
        String line1 = required(request.line1(), "line1", 128);
        String line2 = optional(request.line2(), "line2", 128);
        String city = required(request.city(), "city", 64);
        String stateRegion = optional(request.stateRegion(), "stateRegion", 64);
        String postalCode = required(request.postalCode(), "postalCode", 16);
        String country = country(request.country());

        List<String> changedFields = new ArrayList<>();
        if (!before.addressType().equals(addressType)) {
            changedFields.add("addressType");
        }
        if (!before.line1().equals(line1)) {
            changedFields.add("line1");
        }
        if (!Objects.equals(before.line2(), line2)) {
            changedFields.add("line2");
        }
        if (!before.city().equals(city)) {
            changedFields.add("city");
        }
        if (!Objects.equals(before.stateRegion(), stateRegion)) {
            changedFields.add("stateRegion");
        }
        if (!before.postalCode().equals(postalCode)) {
            changedFields.add("postalCode");
        }
        if (!before.country().equals(country)) {
            changedFields.add("country");
        }
        if (!Objects.equals(before.effectiveDate(), request.effectiveDate())) {
            changedFields.add("effectiveDate");
        }
        if (changedFields.isEmpty()) {
            return before;
        }

        PartyAddress after = new PartyAddress(
                addressId, tenantId, partyId, addressType, line1, line2, city,
                stateRegion, postalCode, country, request.effectiveDate(),
                before.createdAt(), before.createdBy());
        if (addresses.update(after) == 0) {
            throw new PartyChildNotFoundException("Address", addressId);
        }

        Party updated = parties.bumpVersion(tenantId, partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        outbox.insert(OutboxEvent.fromEnvelope(
                PartyEvents.addressUpdated(updated, before, after, changedFields, Instant.now())));
        return after;
    }

    /** Hard-deletes one address and records its last snapshot in the event. */
    @Transactional
    public void deleteAddress(UUID tenantId, UUID partyId, UUID addressId) {
        requireParty(tenantId, partyId);
        PartyAddress deleted = addresses.findById(tenantId, partyId, addressId)
                .orElseThrow(() -> new PartyChildNotFoundException("Address", addressId));
        if (addresses.delete(tenantId, partyId, addressId) == 0) {
            throw new PartyChildNotFoundException("Address", addressId);
        }

        Party updated = parties.bumpVersion(tenantId, partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
        outbox.insert(OutboxEvent.fromEnvelope(
                PartyEvents.addressDeleted(updated, deleted, Instant.now())));
    }

    private Party requireParty(UUID tenantId, UUID partyId) {
        return parties.findById(tenantId, partyId)
                .orElseThrow(() -> new PartyNotFoundException(partyId));
    }

    private static String normalizeType(String value, Set<String> allowed, String field) {
        String normalized = required(value, field, 32).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported " + field + ": " + value + " (expected one of " + allowed + ")");
        }
        return normalized;
    }

    private static String country(String value) {
        String normalized = required(value, "country", 2).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw new IllegalArgumentException(
                    "country must be a two-letter ISO 3166-1 alpha-2 code: " + value);
        }
        return normalized;
    }

    private static String required(String value, String field, int maxLength) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }

    private static String optional(String value, String field, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(
                    field + " must be at most " + maxLength + " characters");
        }
        return trimmed;
    }
}
