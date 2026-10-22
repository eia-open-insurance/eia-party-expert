package com.eia.party.expert;

/**
 * Raised when an identifier would duplicate the unique
 * {@code (tenant_id, party_id, identifier_type, identifier_value)} row.
 */
public class PartyIdentifierConflictException extends RuntimeException {
    public PartyIdentifierConflictException(String identifierType) {
        super("Identifier already exists for this party: " + identifierType);
    }
}
