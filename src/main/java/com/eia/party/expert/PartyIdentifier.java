package com.eia.party.expert;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PartyIdentifier(
        UUID id,
        UUID tenantId,
        UUID partyId,
        String identifierType,
        String identifierValue,
        String issuingAuthority,
        LocalDate effectiveDate,
        Instant createdAt,
        String createdBy
) {}
