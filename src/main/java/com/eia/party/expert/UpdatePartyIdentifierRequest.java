package com.eia.party.expert;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Full replacement of a party identifier's mutable fields. The identifier is
 * addressed by its own id, so the type and value may change; both are
 * validated exactly as on create, every text field is trimmed, the type is
 * upper-cased, and a blank optional field clears the stored value.
 */
public record UpdatePartyIdentifierRequest(
        @NotBlank String identifierType,
        @NotBlank String identifierValue,
        String issuingAuthority,
        LocalDate effectiveDate
) {}
