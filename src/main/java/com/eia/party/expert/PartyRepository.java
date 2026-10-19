package com.eia.party.expert;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PartyRepository {
    private final JdbcTemplate jdbc;

    public PartyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final RowMapper<Party> MAPPER = (rs, rowNum) -> new Party(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getString("party_number"),
            rs.getString("party_type"),
            rs.getString("display_name"),
            rs.getString("legal_name"),
            rs.getString("status"),
            rs.getString("sanctions_status"),
            rs.getString("confidentiality"),
            rs.getInt("version"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    public List<Party> findAll(UUID tenantId) {
        return jdbc.query("""
                SELECT id, tenant_id, party_number, party_type, display_name, legal_name,
                       status, sanctions_status, confidentiality, version, created_at, updated_at
                  FROM party
                 WHERE tenant_id = ?
                 ORDER BY display_name
                """, MAPPER, tenantId);
    }

    public Optional<Party> findById(UUID tenantId, UUID id) {
        return jdbc.query("""
                SELECT id, tenant_id, party_number, party_type, display_name, legal_name,
                       status, sanctions_status, confidentiality, version, created_at, updated_at
                  FROM party
                 WHERE tenant_id = ? AND id = ?
                """, MAPPER, tenantId, id).stream().findFirst();
    }

    public Party create(CreatePartyRequest request) {
        UUID id = UUID.randomUUID();
        String partyNumber = "PTY-" + String.format("%09d", jdbc.queryForObject("SELECT nextval('party_number_seq')", Long.class));
        Instant now = Instant.now();
        jdbc.update("""
                INSERT INTO party (id, tenant_id, party_number, party_type, display_name, legal_name,
                                   legal_name_normalized, status, sanctions_status, confidentiality,
                                   version, record_state, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PROSPECT', 'NOT_SCREENED', 'INTERNAL', 0, 'ACTIVE', ?, ?)
                """, id, request.tenantId(), partyNumber, request.partyType(), request.displayName(),
                request.legalName(), request.legalName().toLowerCase(), java.sql.Timestamp.from(now), java.sql.Timestamp.from(now));
        return findById(request.tenantId(), id).orElseThrow();
    }
}
