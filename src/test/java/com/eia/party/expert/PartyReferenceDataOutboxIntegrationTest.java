package com.eia.party.expert;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class PartyReferenceDataOutboxIntegrationTest {
    @Autowired
    private PartyService partyService;

    @Autowired
    private PartyReferenceDataService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transaction;
    private UUID tenantId;
    private Party party;

    @BeforeEach
    void setUp() {
        transaction = new TransactionTemplate(transactionManager);
        clear();
        tenantId = UUID.randomUUID();
        party = partyService.create(new CreatePartyRequest(
                tenantId, "ORGANIZATION", "Analytical Engines Ltd", "Analytical Engines Limited"));
    }

    @AfterEach
    void tearDown() {
        clear();
    }

    private void clear() {
        jdbc.update("DELETE FROM party_identifier");
        jdbc.update("DELETE FROM party_address");
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM party");
    }

    @Test
    void identifierAddBumpsThePartyAndWritesTheEventInTheSameTransaction() {
        PartyIdentifier identifier = service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest(
                        " tax_id ", " 12-3456789 ", " IRS ", LocalDate.of(2026, 1, 15)));

        assertThat(identifier.identifierType()).isEqualTo("TAX_ID");
        assertThat(identifier.identifierValue()).isEqualTo("12-3456789");
        assertThat(identifier.issuingAuthority()).isEqualTo("IRS");
        assertThat(identifier.createdBy()).isEqualTo("system");
        assertThat(partyVersion()).isEqualTo(1);
        assertThat(count("party_identifier")).isEqualTo(1);

        Map<String, Object> event = jdbc.queryForMap("""
                SELECT event_type, aggregate_type, aggregate_version, status, tenant_id,
                       payload, headers, partition_key
                  FROM outbox_event
                 WHERE event_type = 'party.party.identifier-added'
                """);

        assertThat(event.get("event_type")).isEqualTo("party.party.identifier-added");
        assertThat(event.get("aggregate_type")).isEqualTo("Party");
        assertThat(event.get("aggregate_version")).isEqualTo(2L);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("tenant_id")).isEqualTo(tenantId);
        assertThat(event.get("partition_key")).isEqualTo(party.id().toString());

        String payload = (String) event.get("payload");
        assertThat(payload)
                .contains("\"partyId\":\"" + party.id() + "\"")
                .contains("\"partyNumber\":\"" + party.partyNumber() + "\"")
                .contains("\"version\":1")
                .contains("\"identifierId\":\"" + identifier.id() + "\"")
                .contains("\"identifierType\":\"TAX_ID\"")
                .contains("\"identifierValue\":\"12-3456789\"")
                .contains("\"issuingAuthority\":\"IRS\"")
                .contains("\"effectiveDate\":\"2026-01-15\"")
                .contains("\"createdBy\":\"system\"")
                .contains("\"createdAt\":\"");

        String headers = (String) event.get("headers");
        assertThat(headers)
                .contains("\"book\":\"DIRECT\"")
                .contains("\"vertical\":\"party\"")
                .contains("\"businessKey\":\"" + party.partyNumber() + "\"");
    }

    @Test
    void addressAddBumpsThePartyAndWritesTheEventInTheSameTransaction() {
        PartyAddress address = service.addAddress(tenantId, party.id(),
                new CreatePartyAddressRequest(
                        " primary ", " 1 Infinite Loop ", " Suite 1 ", " Cupertino ",
                        " ca ", " 95014 ", " us ", LocalDate.of(2026, 2, 1)));

        assertThat(address.addressType()).isEqualTo("PRIMARY");
        assertThat(address.line1()).isEqualTo("1 Infinite Loop");
        assertThat(address.line2()).isEqualTo("Suite 1");
        assertThat(address.city()).isEqualTo("Cupertino");
        assertThat(address.stateRegion()).isEqualTo("ca");
        assertThat(address.postalCode()).isEqualTo("95014");
        assertThat(address.country()).isEqualTo("US");
        assertThat(partyVersion()).isEqualTo(1);

        Map<String, Object> event = jdbc.queryForMap("""
                SELECT event_type, aggregate_type, aggregate_version, status, tenant_id,
                       payload, headers, partition_key
                  FROM outbox_event
                 WHERE event_type = 'party.party.address-added'
                """);

        assertThat(event.get("event_type")).isEqualTo("party.party.address-added");
        assertThat(event.get("aggregate_type")).isEqualTo("Party");
        assertThat(event.get("aggregate_version")).isEqualTo(2L);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("tenant_id")).isEqualTo(tenantId);
        assertThat(event.get("partition_key")).isEqualTo(party.id().toString());

        String payload = (String) event.get("payload");
        assertThat(payload)
                .contains("\"partyId\":\"" + party.id() + "\"")
                .contains("\"partyNumber\":\"" + party.partyNumber() + "\"")
                .contains("\"version\":1")
                .contains("\"addressId\":\"" + address.id() + "\"")
                .contains("\"addressType\":\"PRIMARY\"")
                .contains("\"line1\":\"1 Infinite Loop\"")
                .contains("\"line2\":\"Suite 1\"")
                .contains("\"city\":\"Cupertino\"")
                .contains("\"stateRegion\":\"ca\"")
                .contains("\"postalCode\":\"95014\"")
                .contains("\"country\":\"US\"")
                .contains("\"effectiveDate\":\"2026-02-01\"")
                .contains("\"createdBy\":\"system\"");

        String headers = (String) event.get("headers");
        assertThat(headers)
                .contains("\"book\":\"DIRECT\"")
                .contains("\"vertical\":\"party\"")
                .contains("\"businessKey\":\"" + party.partyNumber() + "\"");
    }

    @Test
    void consecutiveIdentifierAddsProduceMonotonicEventVersions() {
        service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("TAX_ID", "A-1", null, null));
        service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("PASSPORT", "P-1", null, null));

        assertThat(partyVersion()).isEqualTo(2);

        List<Map<String, Object>> events = jdbc.queryForList("""
                SELECT aggregate_id, aggregate_version
                  FROM outbox_event
                 WHERE event_type = 'party.party.identifier-added'
                 ORDER BY event_sequence
                """);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("aggregate_id")).isEqualTo(party.id());
        assertThat(events.get(0).get("aggregate_version")).isEqualTo(2L);
        assertThat(events.get(1).get("aggregate_id")).isEqualTo(party.id());
        assertThat(events.get(1).get("aggregate_version")).isEqualTo(3L);
    }

    @Test
    void duplicateIdentifierRollsBackTheVersionBumpAndEvent() {
        service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("TAX_ID", "A-1", null, null));

        assertThatThrownBy(() -> service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("tax_id", " A-1 ", null, null)))
                .isInstanceOf(PartyIdentifierConflictException.class);

        assertThat(count("party_identifier")).isEqualTo(1);
        assertThat(partyVersion()).isEqualTo(1);
        assertThat(count("outbox_event")).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM outbox_event
                 WHERE event_type = 'party.party.identifier-added'
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void rollbackLeavesNoReferenceDataVersionBumpOrEvent() {
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> {
            service.addIdentifier(tenantId, party.id(),
                    new CreatePartyIdentifierRequest("TAX_ID", "A-1", null, null));
            service.addAddress(tenantId, party.id(),
                    new CreatePartyAddressRequest("PRIMARY", "1 Main St", null,
                            "Springfield", null, "12345", "US", null));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class)
                .hasMessage("force rollback");

        assertThat(count("party_identifier")).isZero();
        assertThat(count("party_address")).isZero();
        assertThat(partyVersion()).isZero();
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    @Test
    void referenceDataIsScopedToTheTenantThatOwnsTheParty() {
        UUID otherTenant = UUID.randomUUID();

        assertThatThrownBy(() -> service.addIdentifier(otherTenant, party.id(),
                new CreatePartyIdentifierRequest("TAX_ID", "A-1", null, null)))
                .isInstanceOf(PartyNotFoundException.class);
        assertThatThrownBy(() -> service.addresses(otherTenant, party.id()))
                .isInstanceOf(PartyNotFoundException.class);

        assertThat(count("party_identifier")).isZero();
        assertThat(count("outbox_event")).isEqualTo(1);
    }

    private int partyVersion() {
        return jdbc.queryForObject(
                "SELECT version FROM party WHERE tenant_id = ? AND id = ?",
                Integer.class, tenantId, party.id());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }
}
