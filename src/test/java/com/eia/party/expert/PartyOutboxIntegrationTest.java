package com.eia.party.expert;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PartyOutboxIntegrationTest {
    @Autowired
    private PartyService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        jdbc.update("DELETE FROM party_identifier");
        jdbc.update("DELETE FROM party_address");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM party");
    }

    @Test
    void createWritesThePartyAndCreatedEventTogether() {
        UUID tenantId = UUID.randomUUID();

        Party party = service.create(new CreatePartyRequest(
                tenantId, "PERSON", "Ada Lovelace", "Ada Lovelace"));

        assertThat(party.status()).isEqualTo("PROSPECT");
        assertThat(party.version()).isZero();

        Map<String, Object> event = jdbc.queryForMap("""
                SELECT event_type, aggregate_type, aggregate_version, status, tenant_id,
                       payload, headers, occurred_at, recorded_at, next_attempt_at
                  FROM outbox_event
                 WHERE aggregate_id = ?
                """, party.id());

        assertThat(event.get("event_type")).isEqualTo("party.party.created");
        assertThat(event.get("aggregate_type")).isEqualTo("Party");
        assertThat(event.get("aggregate_version")).isEqualTo(1L);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("tenant_id")).isEqualTo(tenantId);
        assertThat(event.get("occurred_at")).isNotNull();
        assertThat(event.get("recorded_at")).isNotNull();
        assertThat(event.get("next_attempt_at")).isNotNull();

        String payload = (String) event.get("payload");
        assertThat(payload)
                .contains("\"partyId\":\"" + party.id() + "\"")
                .contains("\"partyNumber\":\"" + party.partyNumber() + "\"")
                .contains("\"partyType\":\"PERSON\"")
                .contains("\"status\":\"PROSPECT\"")
                .contains("\"roles\":[]");
        String headers = (String) event.get("headers");
        assertThat(headers)
                .contains("\"book\":\"DIRECT\"")
                .contains("\"vertical\":\"party\"")
                .contains("\"businessKey\":\"" + party.partyNumber() + "\"");
    }

    @Test
    void createRollbackLeavesNeitherThePartyNorTheEvent() {
        UUID tenantId = UUID.randomUUID();

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.create(new CreatePartyRequest(
                    tenantId, "PERSON", "Grace Hopper", "Grace Hopper"));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        assertThat(countByTenant("party", tenantId)).isZero();
        assertThat(countByTenant("outbox_event", tenantId)).isZero();
    }

    @Test
    void statusUpdateWritesTheUpdatedEventInTheSameTransaction() {
        UUID tenantId = UUID.randomUUID();
        Party party = service.create(new CreatePartyRequest(
                tenantId, "PERSON", "Alan Turing", "Alan Turing"));

        Party updated = service.updateStatus(tenantId, party.id(),
                new UpdatePartyStatusRequest("ACTIVE", "identity verified"));

        assertThat(updated.status()).isEqualTo("ACTIVE");
        assertThat(updated.version()).isEqualTo(1);

        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT event_type, aggregate_version, payload
                  FROM outbox_event
                 WHERE aggregate_id = ?
                 ORDER BY event_sequence
                """, party.id());

        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("event_type")).isEqualTo("party.party.created");
        assertThat(events.get(1).get("event_type")).isEqualTo("party.party.updated");
        assertThat(events.get(1).get("aggregate_version")).isEqualTo(2L);

        String payload = (String) events.get(1).get("payload");
        assertThat(payload)
                .contains("\"changedFields\":[\"status\"]")
                .contains("\"version\":1")
                .contains("\"from\":\"PROSPECT\"")
                .contains("\"to\":\"ACTIVE\"")
                .contains("\"reason\":\"identity verified\"");
    }

    @Test
    void statusUpdateRollbackKeepsTheOldStateAndWritesNoEvent() {
        UUID tenantId = UUID.randomUUID();
        Party party = service.create(new CreatePartyRequest(
                tenantId, "PERSON", "Katherine Johnson", "Katherine Johnson"));

        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.updateStatus(tenantId, party.id(),
                    new UpdatePartyStatusRequest("ACTIVE", "verified"));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        Party reloaded = jdbc.queryForObject("""
                SELECT status, version
                  FROM party
                 WHERE tenant_id = ? AND id = ?
                """, (rs, rowNum) -> new Party(
                party.id(), tenantId, party.partyNumber(), party.partyType(),
                party.displayName(), party.legalName(), rs.getString("status"),
                party.sanctionsStatus(), party.confidentiality(), rs.getInt("version"),
                party.createdAt(), party.updatedAt()), tenantId, party.id());

        assertThat(reloaded.status()).isEqualTo("PROSPECT");
        assertThat(reloaded.version()).isZero();
        assertThat(countByTenant("outbox_event", tenantId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_event
                 WHERE aggregate_id = ? AND event_type = 'party.party.updated'
                """, Integer.class, party.id())).isZero();
    }

    private int countByTenant(String table, UUID tenantId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE tenant_id = ?", Integer.class, tenantId);
    }
}
