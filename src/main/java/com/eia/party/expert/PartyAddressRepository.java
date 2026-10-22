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
 * Persistence for party addresses. Addresses have no natural uniqueness rule;
 * a party may hold several addresses of the same type. Every lookup is scoped
 * by tenant and party, so an address of another party or tenant is never
 * visible here.
 */
@Repository
public class PartyAddressRepository {
    private final JdbcTemplate jdbc;

    public PartyAddressRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<PartyAddress> MAPPER = (rs, rowNum) -> new PartyAddress(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getObject("party_id", UUID.class),
            rs.getString("address_type"),
            rs.getString("line1"),
            rs.getString("line2"),
            rs.getString("city"),
            rs.getString("state_region"),
            rs.getString("postal_code"),
            rs.getString("country"),
            rs.getObject("effective_date", LocalDate.class),
            rs.getTimestamp("created_at").toInstant(),
            rs.getString("created_by")
    );

    public List<PartyAddress> findAll(UUID tenantId, UUID partyId) {
        return jdbc.query("""
                SELECT id, tenant_id, party_id, address_type, line1, line2, city,
                       state_region, postal_code, country, effective_date, created_at, created_by
                  FROM party_address
                 WHERE tenant_id = ? AND party_id = ?
                 ORDER BY created_at, id
                """, MAPPER, tenantId, partyId);
    }

    public Optional<PartyAddress> findById(UUID tenantId, UUID partyId, UUID id) {
        return jdbc.query("""
                SELECT id, tenant_id, party_id, address_type, line1, line2, city,
                       state_region, postal_code, country, effective_date, created_at, created_by
                  FROM party_address
                 WHERE tenant_id = ? AND party_id = ? AND id = ?
                """, MAPPER, tenantId, partyId, id).stream().findFirst();
    }

    public PartyAddress insert(PartyAddress address) {
        jdbc.update("""
                INSERT INTO party_address (
                    id, tenant_id, party_id, address_type, line1, line2, city,
                    state_region, postal_code, country, effective_date, created_at, created_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                address.id(), address.tenantId(), address.partyId(), address.addressType(),
                address.line1(), address.line2(), address.city(), address.stateRegion(),
                address.postalCode(), address.country(),
                address.effectiveDate() == null ? null : java.sql.Date.valueOf(address.effectiveDate()),
                Timestamp.from(address.createdAt()), address.createdBy());
        return address;
    }

    /**
     * Replaces the mutable fields of one address. {@code created_at} and
     * {@code created_by} are immutable, so the update never touches them.
     * Returns the affected row count; zero means the row disappeared or does
     * not belong to the party and tenant in the path.
     */
    public int update(PartyAddress address) {
        return jdbc.update("""
                UPDATE party_address
                   SET address_type = ?, line1 = ?, line2 = ?, city = ?,
                       state_region = ?, postal_code = ?, country = ?, effective_date = ?
                 WHERE tenant_id = ? AND party_id = ? AND id = ?
                """,
                address.addressType(), address.line1(), address.line2(), address.city(),
                address.stateRegion(), address.postalCode(), address.country(),
                address.effectiveDate() == null ? null : java.sql.Date.valueOf(address.effectiveDate()),
                address.tenantId(), address.partyId(), address.id());
    }

    /** Hard-deletes one address; returns the affected row count. */
    public int delete(UUID tenantId, UUID partyId, UUID id) {
        return jdbc.update("""
                DELETE FROM party_address
                 WHERE tenant_id = ? AND party_id = ? AND id = ?
                """, tenantId, partyId, id);
    }
}
