package com.eia.party.expert;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Party reference data. The same resources are exposed on the internal expert
 * route and on the public Party path so the domain service and direct
 * consumers can use either edge.
 *
 * <p>The tenant is taken from {@code ?tenantId=} for internal calls or from
 * {@code X-EIA-Tenant} when the request arrives through the gateway. Supplying
 * both with different values is rejected rather than silently overriding the
 * authenticated tenant.</p>
 */
@RestController
@RequestMapping({"/internal/v1/parties", "/api/v1/parties"})
public class PartyReferenceDataController {
    private final PartyReferenceDataService service;

    public PartyReferenceDataController(PartyReferenceDataService service) {
        this.service = service;
    }

    @GetMapping("/{partyId}/identifiers")
    public List<PartyIdentifier> listIdentifiers(
            @PathVariable UUID partyId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader) {
        return service.identifiers(tenant(tenantId, tenantHeader), partyId);
    }

    @PostMapping("/{partyId}/identifiers")
    @ResponseStatus(HttpStatus.CREATED)
    public PartyIdentifier addIdentifier(
            @PathVariable UUID partyId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader,
            @Valid @RequestBody CreatePartyIdentifierRequest request) {
        return service.addIdentifier(tenant(tenantId, tenantHeader), partyId, request);
    }

    @PatchMapping("/{partyId}/identifiers/{identifierId}")
    public PartyIdentifier updateIdentifier(
            @PathVariable UUID partyId,
            @PathVariable UUID identifierId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader,
            @Valid @RequestBody UpdatePartyIdentifierRequest request) {
        return service.updateIdentifier(
                tenant(tenantId, tenantHeader), partyId, identifierId, request);
    }

    @DeleteMapping("/{partyId}/identifiers/{identifierId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteIdentifier(
            @PathVariable UUID partyId,
            @PathVariable UUID identifierId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader) {
        service.deleteIdentifier(tenant(tenantId, tenantHeader), partyId, identifierId);
    }

    @GetMapping("/{partyId}/addresses")
    public List<PartyAddress> listAddresses(
            @PathVariable UUID partyId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader) {
        return service.addresses(tenant(tenantId, tenantHeader), partyId);
    }

    @PostMapping("/{partyId}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    public PartyAddress addAddress(
            @PathVariable UUID partyId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader,
            @Valid @RequestBody CreatePartyAddressRequest request) {
        return service.addAddress(tenant(tenantId, tenantHeader), partyId, request);
    }

    @PatchMapping("/{partyId}/addresses/{addressId}")
    public PartyAddress updateAddress(
            @PathVariable UUID partyId,
            @PathVariable UUID addressId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader,
            @Valid @RequestBody UpdatePartyAddressRequest request) {
        return service.updateAddress(
                tenant(tenantId, tenantHeader), partyId, addressId, request);
    }

    @DeleteMapping("/{partyId}/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAddress(
            @PathVariable UUID partyId,
            @PathVariable UUID addressId,
            @RequestParam(required = false) UUID tenantId,
            @RequestHeader(value = "X-EIA-Tenant", required = false) UUID tenantHeader) {
        service.deleteAddress(tenant(tenantId, tenantHeader), partyId, addressId);
    }

    private static UUID tenant(UUID tenantId, UUID tenantHeader) {
        if (tenantId != null && tenantHeader != null && !tenantId.equals(tenantHeader)) {
            throw new IllegalArgumentException(
                    "tenantId query parameter does not match the X-EIA-Tenant header");
        }
        UUID resolved = tenantHeader != null ? tenantHeader : tenantId;
        if (resolved == null) {
            throw new IllegalArgumentException(
                    "tenantId is required (query parameter tenantId or X-EIA-Tenant header)");
        }
        return resolved;
    }
}
