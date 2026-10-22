package com.eia.party.expert;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

/**
 * Request for a party address. Values are trimmed, the type is upper-cased,
 * and the country is normalized to a two-letter ISO 3166-1 alpha-2 code.
 */
public record CreatePartyAddressRequest(
        @NotBlank String addressType,
        @NotBlank String line1,
        String line2,
        @NotBlank String city,
        String stateRegion,
        @NotBlank String postalCode,
        @NotBlank String country,
        LocalDate effectiveDate
) {}
