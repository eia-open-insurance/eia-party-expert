package com.eia.party.expert;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request for a party identifier. Values are trimmed and the type is
 * upper-cased before persistence; optional fields become null when blank.
 */
public record CreatePartyIdentifierRequest(
        @NotBlank String identifierType,
        @NotBlank String identifierValue,
        String issuingAuthority,
        LocalDate effectiveDate
) {}
