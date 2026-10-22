package com.eia.party.expert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for party identifiers. The unique key
 * {@code (tenant_id, party_id, identifier_type, identifier_value)} is the
 * duplicate guard; the service checks it first and translates a constraint
 * race to a 409. Every lookup is scoped by tenant and party, so an identifier
 * of another party or tenant is never visible here.
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

    public Optional<PartyIdentifier> findById(UUID tenantId, UUID partyId, UUID id) {
        return jdbc.query("""
                SELECT id, tenant_id, party_id, identifier_type, identifier_value,
                       issuing_authority, effective_date, created_at, created_by
                  FROM party_identifier
                 WHERE tenant_id = ? AND party_id = ? AND id = ?
                """, MAPPER, tenantId, partyId, id).stream().findFirst();
    }

    public boolean exists(UUID tenantId, UUID partyId, String identifierType, String identifierValue) {
        return existsExcluding(tenantId, partyId, identifierType, identifierValue, null);
    }

    /**
     * Duplicate guard for an update: the row being updated is excluded so a
     * field edit that keeps the same type and value does not conflict with
     * itself.
     */
    public boolean existsExcluding(UUID tenantId, UUID partyId, String identifierType,
                                   String identifierValue, UUID excludedId) {
        String sql = """
                SELECT count(*)
                  FROM party_identifier
                 WHERE tenant_id = ? AND party_id = ?
                   AND identifier_type = ? AND identifier_value = ?
                """;
        if (excludedId != null) {
            sql += "   AND id <> ?\n";
        }
        Integer count = excludedId == null
                ? jdbc.queryForObject(sql, Integer.class, tenantId, partyId,
                        identifierType, identifierValue)
                : jdbc.queryForObject(sql, Integer.class, tenantId, partyId,
                        identifierType, identifierValue, excludedId);
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

    /**
     * Replaces the mutable fields of one identifier. {@code created_at} and
     * {@code created_by} are immutable, so the update never touches them.
     * Returns the affected row count; zero means the row disappeared or does
     * not belong to the party and tenant in the path.
     */
    public int update(PartyIdentifier identifier) {
        return jdbc.update("""
                UPDATE party_identifier
                   SET identifier_type = ?, identifier_value = ?,
                       issuing_authority = ?, effective_date = ?
                 WHERE tenant_id = ? AND party_id = ? AND id = ?
                """,
                identifier.identifierType(), identifier.identifierValue(),
                identifier.issuingAuthority(),
                identifier.effectiveDate() == null ? null : java.sql.Date.valueOf(identifier.effectiveDate()),
                identifier.tenantId(), identifier.partyId(), identifier.id());
    }

    /** Hard-deletes one identifier; returns the affected row count. */
    public int delete(UUID tenantId, UUID partyId, UUID id) {
        return jdbc.update("""
                DELETE FROM party_identifier
                 WHERE tenant_id = ? AND party_id = ? AND id = ?
                """, tenantId, partyId, id);
    }
}
