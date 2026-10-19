package com.eia.party.expert;

import java.time.Instant;
import java.util.UUID;

public record Party(
        UUID id,
        UUID tenantId,
        String partyNumber,
        String partyType,
        String displayName,
        String legalName,
        String status,
        String sanctionsStatus,
        String confidentiality,
        int version,
        Instant createdAt,
        Instant updatedAt
) {}
