package com.eia.party.expert;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Full replacement of a party address's mutable fields. Values are normalized
 * exactly as on create: text is trimmed, the type is upper-cased, the country
 * becomes a two-letter ISO 3166-1 alpha-2 code, and a blank optional field
 * clears the stored value.
 */
public record UpdatePartyAddressRequest(
        @NotBlank String addressType,
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        String stateRegion,
        @NotBlank String postalCode,
        @NotBlank String country,
        LocalDate effectiveDate
) {}
