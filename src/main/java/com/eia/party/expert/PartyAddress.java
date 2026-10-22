package com.eia.party.expert;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PartyAddress(
        UUID id,
        UUID tenantId,
        UUID partyId,
        String addressType,
        String line1,
        String line2,
        String city,
        String stateRegion,
        String postalCode,
        String country,
        LocalDate effectiveDate,
        Instant createdAt,
        String createdBy
) {}
