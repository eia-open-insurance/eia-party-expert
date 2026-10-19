package com.eia.party.expert;

import java.util.UUID;

public class PartyNotFoundException extends RuntimeException {
    public PartyNotFoundException(UUID id) {
        super("Party not found: " + id);
    }
}
