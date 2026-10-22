package com.eia.party.expert;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartyReferenceDataApiTest {
    private static final String API = "/api/v1/parties";
    private static final String INTERNAL = "/internal/v1/parties";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    private UUID tenantId;
    private UUID partyId;

    @BeforeEach
    void setUp() throws Exception {
        clear();
        tenantId = UUID.randomUUID();
        partyId = createParty(tenantId);
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
    void identifierCanBeAddedAndListedForTheParty() throws Exception {
        mockMvc.perform(get(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier(" tax_id ", " 123-45-6789 ", " IRS ", "2026-01-15")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.partyId").value(partyId.toString()))
                .andExpect(jsonPath("$.identifierType").value("TAX_ID"))
                .andExpect(jsonPath("$.identifierValue").value("123-45-6789"))
                .andExpect(jsonPath("$.issuingAuthority").value("IRS"))
                .andExpect(jsonPath("$.effectiveDate").value("2026-01-15"))
                .andExpect(jsonPath("$.createdBy").value("system"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].identifierType").value("TAX_ID"))
                .andExpect(jsonPath("$[0].identifierValue").value("123-45-6789"))
                .andExpect(jsonPath("$[0].issuingAuthority").value("IRS"))
                .andExpect(jsonPath("$[0].effectiveDate").value("2026-01-15"));

        assertThat(jdbc.queryForObject("""
                SELECT identifier_value FROM party_identifier
                 WHERE tenant_id = ? AND party_id = ?
                """, String.class, tenantId, partyId)).isEqualTo("123-45-6789");
    }

    @Test
    void identifierValidationRejectsBlankUnknownOverlongAndMalformedValues() throws Exception {
        assertBadIdentifier(identifier("TAX_ID", "   ", null, null));
        assertBadIdentifier(identifier("NATIONAL_ID", "X1", null, null));
        assertBadIdentifier(identifier("TAX_ID", "X".repeat(129), null, null));
        assertBadIdentifier(identifier("TAX_ID", "X1", "A".repeat(129), null));
        assertBadIdentifier(identifier("TAX_ID", "X1", null, "not-a-date"));
        assertBadIdentifier("{\"identifierValue\":\"X1\"}");

        assertThat(count("party_identifier")).isZero();
    }

    @Test
    void duplicateIdentifierIsRejectedWith409AndWritesNothingExtra() throws Exception {
        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("TAX_ID", "123-45-6789", null, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier(" tax_id ", " 123-45-6789 ", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertThat(count("party_identifier")).isEqualTo(1);
        assertThat(countIdentifierEvents()).isEqualTo(1);
        assertThat(partyVersion()).isEqualTo(1);
    }

    @Test
    void identifierUniquenessIsScopedToTheParty() throws Exception {
        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("TAX_ID", "123-45-6789", null, null)))
                .andExpect(status().isCreated());

        UUID otherParty = createParty(tenantId);
        mockMvc.perform(post(API + "/{partyId}/identifiers", otherParty)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("TAX_ID", "123-45-6789", null, null)))
                .andExpect(status().isCreated());

        assertThat(count("party_identifier")).isEqualTo(2);
    }

    @Test
    void addressCanBeAddedAndListedWithNormalizedValues() throws Exception {
        mockMvc.perform(post(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(address(" primary ", " 1 Infinite Loop ", "  ",
                                " Cupertino ", " CA ", " 95014 ", " us ", "2026-02-01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.addressType").value("PRIMARY"))
                .andExpect(jsonPath("$.line1").value("1 Infinite Loop"))
                .andExpect(jsonPath("$.line2").value(nullValue()))
                .andExpect(jsonPath("$.city").value("Cupertino"))
                .andExpect(jsonPath("$.stateRegion").value("CA"))
                .andExpect(jsonPath("$.postalCode").value("95014"))
                .andExpect(jsonPath("$.country").value("US"))
                .andExpect(jsonPath("$.effectiveDate").value("2026-02-01"))
                .andExpect(jsonPath("$.createdBy").value("system"));

        mockMvc.perform(get(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].addressType").value("PRIMARY"))
                .andExpect(jsonPath("$[0].country").value("US"));

        mockMvc.perform(post(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(address("MAILING", "PO Box 7", null, "Cupertino",
                                null, "95015", "US", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void addressValidationRejectsBadCountryPostalTypeAndRequiredFields() throws Exception {
        assertBadAddress(address("PRIMARY", "1 Main St", null, "Springfield", null,
                "12345", "USA", null));
        assertBadAddress(address("PRIMARY", "1 Main St", null, "Springfield", null,
                "12345", "U1", null));
        assertBadAddress(address("PRIMARY", "1 Main St", null, "Springfield", null,
                "12345678901234567", "US", null));
        assertBadAddress(address("PRIMARY", "   ", null, "Springfield", null,
                "12345", "US", null));
        assertBadAddress(address("HOME", "1 Main St", null, "Springfield", null,
                "12345", "US", null));
        assertBadAddress(address("PRIMARY", "1 Main St", "L".repeat(129), "Springfield", null,
                "12345", "US", null));

        assertThat(count("party_address")).isZero();
    }

    @Test
    void missingPartyIs404ForIdentifiersAndAddresses() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(get(API + "/{partyId}/identifiers", missing)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(post(API + "/{partyId}/identifiers", missing)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("TAX_ID", "123", null, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get(API + "/{partyId}/addresses", missing)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(API + "/{partyId}/addresses", missing)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(address("PRIMARY", "1 Main St", null, "Springfield", null,
                                "12345", "US", null)))
                .andExpect(status().isNotFound());
    }

    @Test
    void tenantIsolationHidesAndRejectsPartiesOfAnotherTenant() throws Exception {
        UUID otherTenant = UUID.randomUUID();

        mockMvc.perform(get(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", otherTenant.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", otherTenant.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("TAX_ID", "123", null, null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", otherTenant.toString()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", otherTenant.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(address("PRIMARY", "1 Main St", null, "Springfield", null,
                                "12345", "US", null)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("PASSPORT", "P1234567", null, null)))
                .andExpect(status().isCreated());

        UUID otherParty = createParty(tenantId);
        mockMvc.perform(get(API + "/{partyId}/identifiers", otherParty)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void internalRouteAndTenantHeaderAreAcceptedAndConflictsRejected() throws Exception {
        mockMvc.perform(post(INTERNAL + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("REGISTRATION", "REG-77", null, null)))
                .andExpect(status().isCreated());

        mockMvc.perform(post(API + "/{partyId}/addresses", partyId)
                        .header("X-EIA-Tenant", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(address("BILLING", "2 Main St", null, "Springfield",
                                null, "12345", "US", null)))
                .andExpect(status().isCreated());

        mockMvc.perform(get(API + "/{partyId}/addresses", partyId)
                        .header("X-EIA-Tenant", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        mockMvc.perform(get(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .header("X-EIA-Tenant", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier("LICENSE", "L-1", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void identifierCanBeUpdatedAndDeleted() throws Exception {
        UUID identifierId = addIdentifier(" tax_id ", " 123-45-6789 ", " IRS ", "2026-01-15");

        mockMvc.perform(patch(INTERNAL + "/{partyId}/identifiers/{identifierId}", partyId, identifierId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier(" passport ", " P-123 ", " State Department ", "2026-03-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(identifierId.toString()))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.partyId").value(partyId.toString()))
                .andExpect(jsonPath("$.identifierType").value("PASSPORT"))
                .andExpect(jsonPath("$.identifierValue").value("P-123"))
                .andExpect(jsonPath("$.issuingAuthority").value("State Department"))
                .andExpect(jsonPath("$.effectiveDate").value("2026-03-01"))
                .andExpect(jsonPath("$.createdBy").value("system"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        mockMvc.perform(get(INTERNAL + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].identifierType").value("PASSPORT"))
                .andExpect(jsonPath("$[0].identifierValue").value("P-123"));

        mockMvc.perform(delete(INTERNAL + "/{partyId}/identifiers/{identifierId}", partyId, identifierId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(INTERNAL + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assertThat(count("party_identifier")).isZero();
        assertThat(partyVersion()).isEqualTo(3);
        assertThat(countEvent("party.party.identifier-added")).isEqualTo(1);
        assertThat(countEvent("party.party.identifier-updated")).isEqualTo(1);
        assertThat(countEvent("party.party.identifier-deleted")).isEqualTo(1);
    }

    @Test
    void identifierUpdateRejectsDuplicatesAndInvalidFields() throws Exception {
        addIdentifier("TAX_ID", "123-45-6789", null, null);
        UUID second = addIdentifier("PASSPORT", "P-1", null, null);

        mockMvc.perform(patch(INTERNAL + "/{partyId}/identifiers/{identifierId}", partyId, second)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier(" tax_id ", " 123-45-6789 ", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));

        assertBadIdentifierUpdate(second, identifier("NATIONAL_ID", "P-1", null, null));
        assertBadIdentifierUpdate(second, identifier("PASSPORT", "   ", null, null));
        assertBadIdentifierUpdate(second, identifier("PASSPORT", "P-1", "A".repeat(129), null));
        assertBadIdentifierUpdate(second, identifier("PASSPORT", "P-1", null, "not-a-date"));

        assertThat(count("party_identifier")).isEqualTo(2);
        assertThat(partyVersion()).isEqualTo(2);
        assertThat(countEvent("party.party.identifier-updated")).isZero();
    }

    @Test
    void identifierUpdateAndDeleteAre404ForUnknownPartyChildOrTenant() throws Exception {
        UUID identifierId = addIdentifier("TAX_ID", "123-45-6789", null, null);
        UUID otherParty = createParty(tenantId);
        UUID otherTenant = UUID.randomUUID();
        UUID unknownIdentifier = UUID.randomUUID();
        String update = identifier("TAX_ID", "555", null, null);

        mockMvc.perform(patch(API + "/{partyId}/identifiers/{identifierId}", otherParty, identifierId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(delete(API + "/{partyId}/identifiers/{identifierId}", otherParty, identifierId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(API + "/{partyId}/identifiers/{identifierId}", partyId, identifierId)
                        .param("tenantId", otherTenant.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(API + "/{partyId}/identifiers/{identifierId}", partyId, identifierId)
                        .param("tenantId", otherTenant.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(API + "/{partyId}/identifiers/{identifierId}", partyId, unknownIdentifier)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(API + "/{partyId}/identifiers/{identifierId}", partyId, unknownIdentifier)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(API + "/{partyId}/identifiers/{identifierId}", UUID.randomUUID(), identifierId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound());

        assertThat(count("party_identifier")).isEqualTo(1);
        assertThat(partyVersion()).isEqualTo(1);
        assertThat(countEvent("party.party.identifier-updated")).isZero();
        assertThat(countEvent("party.party.identifier-deleted")).isZero();
    }

    @Test
    void addressCanBeUpdatedAndDeleted() throws Exception {
        UUID addressId = addAddress("PRIMARY", "1 Main St", null, "Springfield",
                "IL", "62701", "US", "2026-01-01");

        mockMvc.perform(patch(INTERNAL + "/{partyId}/addresses/{addressId}", partyId, addressId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(address(" mailing ", " 2 Main St ", " Suite 5 ", " Shelbyville ",
                                " ", " 62702 ", " ca ", "2026-04-01")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(addressId.toString()))
                .andExpect(jsonPath("$.addressType").value("MAILING"))
                .andExpect(jsonPath("$.line1").value("2 Main St"))
                .andExpect(jsonPath("$.line2").value("Suite 5"))
                .andExpect(jsonPath("$.city").value("Shelbyville"))
                .andExpect(jsonPath("$.stateRegion").value(nullValue()))
                .andExpect(jsonPath("$.postalCode").value("62702"))
                .andExpect(jsonPath("$.country").value("CA"))
                .andExpect(jsonPath("$.effectiveDate").value("2026-04-01"))
                .andExpect(jsonPath("$.createdBy").value("system"));

        mockMvc.perform(get(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].addressType").value("MAILING"))
                .andExpect(jsonPath("$[0].country").value("CA"));

        mockMvc.perform(delete(INTERNAL + "/{partyId}/addresses/{addressId}", partyId, addressId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());

        assertThat(count("party_address")).isZero();
        assertThat(partyVersion()).isEqualTo(3);
        assertThat(countEvent("party.party.address-added")).isEqualTo(1);
        assertThat(countEvent("party.party.address-updated")).isEqualTo(1);
        assertThat(countEvent("party.party.address-deleted")).isEqualTo(1);
    }

    @Test
    void addressUpdateValidatesAndLeavesTheRowUnchanged() throws Exception {
        UUID addressId = addAddress("PRIMARY", "1 Main St", null, "Springfield",
                null, "62701", "US", null);

        assertBadAddressUpdate(addressId, address("PRIMARY", "1 Main St", null, "Springfield",
                null, "62701", "USA", null));
        assertBadAddressUpdate(addressId, address("PRIMARY", "1 Main St", null, "Springfield",
                null, "62701", "U1", null));
        assertBadAddressUpdate(addressId, address("PRIMARY", "1 Main St", null, "Springfield",
                null, "12345678901234567", "US", null));
        assertBadAddressUpdate(addressId, address("PRIMARY", "   ", null, "Springfield",
                null, "62701", "US", null));
        assertBadAddressUpdate(addressId, address("HOME", "1 Main St", null, "Springfield",
                null, "62701", "US", null));
        assertBadAddressUpdate(addressId, address("PRIMARY", "1 Main St", "L".repeat(129),
                "Springfield", null, "62701", "US", null));

        assertThat(jdbc.queryForObject("""
                SELECT address_type FROM party_address
                 WHERE tenant_id = ? AND party_id = ? AND id = ?
                """, String.class, tenantId, partyId, addressId)).isEqualTo("PRIMARY");
        assertThat(partyVersion()).isEqualTo(1);
        assertThat(countEvent("party.party.address-updated")).isZero();
    }

    @Test
    void addressUpdateAndDeleteAre404ForUnknownPartyChildOrTenant() throws Exception {
        UUID addressId = addAddress("PRIMARY", "1 Main St", null, "Springfield",
                null, "62701", "US", null);
        UUID otherParty = createParty(tenantId);
        UUID otherTenant = UUID.randomUUID();
        String update = address("MAILING", "2 Main St", null, "Springfield",
                null, "62701", "US", null);

        mockMvc.perform(patch(API + "/{partyId}/addresses/{addressId}", otherParty, addressId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(API + "/{partyId}/addresses/{addressId}", otherParty, addressId)
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(API + "/{partyId}/addresses/{addressId}", partyId, addressId)
                        .param("tenantId", otherTenant.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(API + "/{partyId}/addresses/{addressId}", partyId, addressId)
                        .param("tenantId", otherTenant.toString()))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch(API + "/{partyId}/addresses/{addressId}", partyId, UUID.randomUUID())
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(API + "/{partyId}/addresses/{addressId}", partyId, UUID.randomUUID())
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound());

        assertThat(count("party_address")).isEqualTo(1);
        assertThat(partyVersion()).isEqualTo(1);
        assertThat(countEvent("party.party.address-updated")).isZero();
        assertThat(countEvent("party.party.address-deleted")).isZero();
    }

    private UUID addIdentifier(String type, String value, String authority, String effectiveDate)
            throws Exception {
        String response = mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(identifier(type, value, authority, effectiveDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private UUID addAddress(String type, String line1, String line2, String city,
                            String stateRegion, String postalCode, String country,
                            String effectiveDate) throws Exception {
        String response = mockMvc.perform(post(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(address(type, line1, line2, city, stateRegion,
                                postalCode, country, effectiveDate)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private void assertBadIdentifierUpdate(UUID identifierId, String body) throws Exception {
        mockMvc.perform(patch(API + "/{partyId}/identifiers/{identifierId}", partyId, identifierId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void assertBadAddressUpdate(UUID addressId, String body) throws Exception {
        mockMvc.perform(patch(API + "/{partyId}/addresses/{addressId}", partyId, addressId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void assertBadIdentifier(String body) throws Exception {
        mockMvc.perform(post(API + "/{partyId}/identifiers", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private void assertBadAddress(String body) throws Exception {
        mockMvc.perform(post(API + "/{partyId}/addresses", partyId)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private String identifier(String type, String value, String authority, String effectiveDate)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("identifierType", type);
        body.put("identifierValue", value);
        if (authority != null) {
            body.put("issuingAuthority", authority);
        }
        if (effectiveDate != null) {
            body.put("effectiveDate", effectiveDate);
        }
        return json.writeValueAsString(body);
    }

    private String address(String type, String line1, String line2, String city,
                           String stateRegion, String postalCode, String country,
                           String effectiveDate) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("addressType", type);
        body.put("line1", line1);
        body.put("line2", line2);
        body.put("city", city);
        body.put("stateRegion", stateRegion);
        body.put("postalCode", postalCode);
        body.put("country", country);
        body.put("effectiveDate", effectiveDate);
        return json.writeValueAsString(body);
    }

    private UUID createParty(UUID tenant) throws Exception {
        String body = """
                {"tenantId":"%s","partyType":"PERSON",
                 "displayName":"Ada Lovelace","legalName":"Ada Lovelace"}
                """.formatted(tenant);
        String response = mockMvc.perform(post(INTERNAL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partyNumber").value(startsWith("PTY-")))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(response).get("id").asText());
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private int countIdentifierEvents() {
        return countEvent("party.party.identifier-added");
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
                Integer.class, tenantId, partyId);
    }
}
