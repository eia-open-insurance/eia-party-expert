package com.eia.party.expert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Append-only persistence for party identifiers. The unique key
 * {@code (tenant_id, party_id, identifier_type, identifier_value)} is the
 * duplicate guard; the service checks it first and translates a constraint
 * race to a 409.
 */
@Repository
public class PartyIdentifierRepository {
    private final JdbcTemplate jdbc;

    public PartyIdentifierRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<PartyIdentifier> MAPPER = (rs, rowNum) -> new PartyIdentifier(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("party_id", UUID.class),
            rs.getString("identifier_type"),
            rs.getString("identifier_value"),
            rs.getString("issuing_authority"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("created_by")
    );

    public List<PartyIdentifier> findAll(UUID tenantId, UUID partyId) {
        return jdbc.query("""
                SELECT id, tenant_id, party_id, identifier_type, identifier_value,
                       issuing_authority, effective_date, created_at, created_by
                  FROM party_identifier
                 WHERE tenant_id = ? AND party_id = ?
                 ORDER BY created_at, id
                """, MAPPER, tenantId, partyId);
    }

    public boolean exists(UUID tenantId, UUID partyId, String identifierType, String identifierValue) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*)
                  FROM party_identifier
                 WHERE tenant_id = ? AND party_id = ?
                   AND identifier_type = ? AND identifier_value = ?
                """, Integer.class, tenantId, partyId, identifierType, identifierValue);
        return count != null && count > 0;
    }

    public PartyIdentifier insert(PartyIdentifier identifier) {
        jdbc.update("""
                INSERT INTO party_identifier (
                    id, tenant_id, party_id, identifier_type, identifier_value,
                    issuing_authority, effective_date, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                identifier.id(), identifier.tenantId(), identifier.partyId(),
                identifier.identifierType(), identifier.identifierValue(),
                identifier.issuingAuthority(),
                identifier.effectiveDate() == null ? null : java.sql.Date.valueOf(identifier.effectiveDate()),
                Timestamp.from(identifier.createdAt()), identifier.createdBy());
        return identifier;
    }
}
