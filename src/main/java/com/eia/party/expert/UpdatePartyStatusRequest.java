package com.eia.party.expert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request for a party status transition. The status is validated against the
 * PartyStatus enumeration in SPEC 11.1.5.
 */
public record UpdatePartyStatusRequest(
        @NotBlank String status,
        @Size(max = 500) String reason
) {
}
