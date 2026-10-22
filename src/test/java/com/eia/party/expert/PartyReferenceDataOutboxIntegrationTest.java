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

    @Test
    void identifierUpdateBumpsThePartyAndWritesChangedFields() {
        PartyIdentifier created = service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest(
                        "TAX_ID", "A-1", "IRS", LocalDate.of(2026, 1, 15)));

        PartyIdentifier updated = service.updateIdentifier(tenantId, party.id(), created.id(),
                new UpdatePartyIdentifierRequest(
                        " passport ", " P-9 ", " State ", LocalDate.of(2026, 3, 1)));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.tenantId()).isEqualTo(tenantId);
        assertThat(updated.partyId()).isEqualTo(party.id());
        assertThat(updated.identifierType()).isEqualTo("PASSPORT");
        assertThat(updated.identifierValue()).isEqualTo("P-9");
        assertThat(updated.issuingAuthority()).isEqualTo("State");
        assertThat(updated.effectiveDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(updated.createdBy()).isEqualTo("system");
        assertThat(updated.createdAt())
                .isEqualTo(service.identifiers(tenantId, party.id()).get(0).createdAt());
        assertThat(partyVersion()).isEqualTo(2);
        assertThat(count("party_identifier")).isEqualTo(1);

        Map<String, Object> event = event("party.party.identifier-updated");
        assertThat(event.get("aggregate_type")).isEqualTo("Party");
        assertThat(event.get("aggregate_version")).isEqualTo(3L);
        assertThat(event.get("status")).isEqualTo("PENDING");
        assertThat(event.get("tenant_id")).isEqualTo(tenantId);
        assertThat(event.get("partition_key")).isEqualTo(party.id().toString());

        String payload = (String) event.get("payload");
        assertThat(payload)
                .contains("\"partyId\":\"" + party.id() + "\"")
                .contains("\"partyNumber\":\"" + party.partyNumber() + "\"")
                .contains("\"version\":2")
                .contains("\"identifierId\":\"" + created.id() + "\"")
                .contains("\"changedFields\":[\"identifierType\",\"identifierValue\","
                        + "\"issuingAuthority\",\"effectiveDate\"]")
                .contains("\"from\":{")
                .contains("\"to\":{")
                .contains("\"identifierType\":\"TAX_ID\"")
                .contains("\"identifierType\":\"PASSPORT\"")
                .contains("\"identifierValue\":\"A-1\"")
                .contains("\"identifierValue\":\"P-9\"")
                .contains("\"issuingAuthority\":\"IRS\"")
                .contains("\"issuingAuthority\":\"State\"");
    }

    @Test
    void unchangedIdentifierUpdateWritesNoEventAndDoesNotBumpTheParty() {
        PartyIdentifier created = service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("TAX_ID", "A-1", null, null));

        PartyIdentifier result = service.updateIdentifier(tenantId, party.id(), created.id(),
                new UpdatePartyIdentifierRequest(" tax_id ", " A-1 ", null, null));

        assertThat(result.id()).isEqualTo(created.id());
        assertThat(result.identifierType()).isEqualTo("TAX_ID");
        assertThat(result.identifierValue()).isEqualTo("A-1");
        assertThat(result.createdAt())
                .isEqualTo(service.identifiers(tenantId, party.id()).get(0).createdAt());
        assertThat(partyVersion()).isEqualTo(1);
        assertThat(countEvent("party.party.identifier-updated")).isZero();
        assertThat(count("outbox_event")).isEqualTo(2);
    }

    @Test
    void identifierDeleteBumpsThePartyAndWritesTheDeletedSnapshot() {
        PartyIdentifier created = service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest(
                        "TAX_ID", "A-1", "IRS", LocalDate.of(2026, 1, 15)));

        service.deleteIdentifier(tenantId, party.id(), created.id());

        assertThat(count("party_identifier")).isZero();
        assertThat(partyVersion()).isEqualTo(2);

        Map<String, Object> event = event("party.party.identifier-deleted");
        assertThat(event.get("aggregate_type")).isEqualTo("Party");
        assertThat(event.get("aggregate_version")).isEqualTo(3L);
        assertThat(event.get("partition_key")).isEqualTo(party.id().toString());

        String payload = (String) event.get("payload");
        assertThat(payload)
                .contains("\"partyId\":\"" + party.id() + "\"")
                .contains("\"partyNumber\":\"" + party.partyNumber() + "\"")
                .contains("\"version\":2")
                .contains("\"identifierId\":\"" + created.id() + "\"")
                .contains("\"identifierType\":\"TAX_ID\"")
                .contains("\"identifierValue\":\"A-1\"")
                .contains("\"issuingAuthority\":\"IRS\"")
                .contains("\"effectiveDate\":\"2026-01-15\"")
                .contains("\"createdBy\":\"system\"")
                .contains("\"createdAt\":\"");

        assertThatThrownBy(() -> service.deleteIdentifier(tenantId, party.id(), created.id()))
                .isInstanceOf(PartyChildNotFoundException.class);
        assertThat(partyVersion()).isEqualTo(2);
        assertThat(countEvent("party.party.identifier-deleted")).isEqualTo(1);
    }

    @Test
    void duplicateIdentifierUpdateConflictsAndWritesNothing() {
        service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("TAX_ID", "A-1", null, null));
        PartyIdentifier second = service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("PASSPORT", "P-1", null, null));

        assertThatThrownBy(() -> service.updateIdentifier(tenantId, party.id(), second.id(),
                new UpdatePartyIdentifierRequest(" tax_id ", " A-1 ", null, null)))
                .isInstanceOf(PartyIdentifierConflictException.class);

        assertThat(partyVersion()).isEqualTo(2);
        assertThat(count("outbox_event")).isEqualTo(3);
        assertThat(countEvent("party.party.identifier-updated")).isZero();
        assertThat(service.identifiers(tenantId, party.id()))
                .extracting(PartyIdentifier::identifierType)
                .containsExactlyInAnyOrder("TAX_ID", "PASSPORT");
    }

    @Test
    void addressUpdateAndDeleteBumpThePartyAndWriteEvents() {
        PartyAddress created = service.addAddress(tenantId, party.id(),
                new CreatePartyAddressRequest(
                        "PRIMARY", "1 Main St", null, "Springfield",
                        "IL", "62701", "US", LocalDate.of(2026, 1, 1)));

        PartyAddress updated = service.updateAddress(tenantId, party.id(), created.id(),
                new UpdatePartyAddressRequest(
                        " mailing ", " 2 Main St ", " Suite 5 ", " Shelbyville ",
                        " ", " 62702 ", " ca ", LocalDate.of(2026, 4, 1)));

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.addressType()).isEqualTo("MAILING");
        assertThat(updated.line1()).isEqualTo("2 Main St");
        assertThat(updated.line2()).isEqualTo("Suite 5");
        assertThat(updated.city()).isEqualTo("Shelbyville");
        assertThat(updated.stateRegion()).isNull();
        assertThat(updated.postalCode()).isEqualTo("62702");
        assertThat(updated.country()).isEqualTo("CA");
        assertThat(updated.effectiveDate()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(updated.createdBy()).isEqualTo("system");
        assertThat(partyVersion()).isEqualTo(2);

        Map<String, Object> updatedEvent = event("party.party.address-updated");
        assertThat(updatedEvent.get("aggregate_version")).isEqualTo(3L);
        String updatedPayload = (String) updatedEvent.get("payload");
        assertThat(updatedPayload)
                .contains("\"addressId\":\"" + created.id() + "\"")
                .contains("\"changedFields\":[\"addressType\",\"line1\",\"line2\",\"city\","
                        + "\"stateRegion\",\"postalCode\",\"country\",\"effectiveDate\"]")
                .contains("\"from\":{")
                .contains("\"to\":{")
                .contains("\"addressType\":\"PRIMARY\"")
                .contains("\"addressType\":\"MAILING\"")
                .contains("\"line1\":\"1 Main St\"")
                .contains("\"line1\":\"2 Main St\"")
                .contains("\"country\":\"US\"")
                .contains("\"country\":\"CA\"");

        service.deleteAddress(tenantId, party.id(), created.id());

        assertThat(count("party_address")).isZero();
        assertThat(partyVersion()).isEqualTo(3);

        Map<String, Object> deletedEvent = event("party.party.address-deleted");
        assertThat(deletedEvent.get("aggregate_version")).isEqualTo(4L);
        String deletedPayload = (String) deletedEvent.get("payload");
        assertThat(deletedPayload)
                .contains("\"partyId\":\"" + party.id() + "\"")
                .contains("\"partyNumber\":\"" + party.partyNumber() + "\"")
                .contains("\"version\":3")
                .contains("\"addressId\":\"" + created.id() + "\"")
                .contains("\"addressType\":\"MAILING\"")
                .contains("\"line1\":\"2 Main St\"")
                .contains("\"line2\":\"Suite 5\"")
                .contains("\"city\":\"Shelbyville\"")
                .contains("\"postalCode\":\"62702\"")
                .contains("\"country\":\"CA\"")
                .contains("\"effectiveDate\":\"2026-04-01\"")
                .contains("\"createdBy\":\"system\"")
                .contains("\"createdAt\":\"");
    }

    @Test
    void childUpdateAndDeleteAreScopedToTheOwningPartyAndTenant() {
        PartyIdentifier identifier = service.addIdentifier(tenantId, party.id(),
                new CreatePartyIdentifierRequest("TAX_ID", "A-1", null, null));
        UUID otherTenant = UUID.randomUUID();
        Party otherParty = partyService.create(new CreatePartyRequest(
                tenantId, "PERSON", "Other Party", "Other Party"));

        assertThatThrownBy(() -> service.updateIdentifier(tenantId, otherParty.id(), identifier.id(),
                new UpdatePartyIdentifierRequest("PASSPORT", "P-1", null, null)))
                .isInstanceOf(PartyChildNotFoundException.class);
        assertThatThrownBy(() -> service.deleteIdentifier(tenantId, otherParty.id(), identifier.id()))
                .isInstanceOf(PartyChildNotFoundException.class);
        assertThatThrownBy(() -> service.updateIdentifier(otherTenant, party.id(), identifier.id(),
                new UpdatePartyIdentifierRequest("PASSPORT", "P-1", null, null)))
                .isInstanceOf(PartyNotFoundException.class);
        assertThatThrownBy(() -> service.deleteIdentifier(otherTenant, party.id(), identifier.id()))
                .isInstanceOf(PartyNotFoundException.class);

        assertThatThrownBy(() -> service.updateAddress(tenantId, party.id(), UUID.randomUUID(),
                new UpdatePartyAddressRequest("PRIMARY", "1 Main St", null, "Springfield",
                        null, "62701", "US", null)))
                .isInstanceOf(PartyChildNotFoundException.class);
        assertThatThrownBy(() -> service.deleteAddress(tenantId, party.id(), UUID.randomUUID()))
                .isInstanceOf(PartyChildNotFoundException.class);

        assertThat(count("party_identifier")).isEqualTo(1);
        assertThat(service.identifiers(tenantId, party.id()))
                .extracting(PartyIdentifier::identifierValue)
                .containsExactly("A-1");
        assertThat(partyVersion()).isEqualTo(1);
        assertThat(countEvent("party.party.identifier-updated")).isZero();
        assertThat(countEvent("party.party.identifier-deleted")).isZero();
    }

    private Map<String, Object> event(String eventType) {
        return jdbc.queryForMap("""
                SELECT event_type, aggregate_type, aggregate_version, status, tenant_id,
                       payload, headers, partition_key
                  FROM outbox_event
                 WHERE event_type = ?
                """, eventType);
    }

    private int countEvent(String eventType) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM outbox_event
                 WHERE event_type = ?
                """, Integer.class, eventType);
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
