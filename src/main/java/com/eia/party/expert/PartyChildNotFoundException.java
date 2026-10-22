package com.eia.party.expert;

import java.util.UUID;

/**
 * Raised when an identifier or address is missing, or belongs to another
 * party or tenant. The message deliberately does not distinguish those cases,
 * so a 404 cannot confirm that a foreign child row exists.
 */
public class PartyChildNotFoundException extends RuntimeException {
    public PartyChildNotFoundException(String resource, UUID id) {
        super(resource + " not found: " + id);
    }
}
