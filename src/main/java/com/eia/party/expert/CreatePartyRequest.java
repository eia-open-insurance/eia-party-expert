package com.eia.party.expert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePartyRequest(
        @NotNull UUID tenantId,
        @NotBlank String partyType,
        @NotBlank String displayName,
        @NotBlank String legalName
) {}
