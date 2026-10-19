package com.eia.party.expert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PartyApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM outbox_event");
        jdbc.update("DELETE FROM party");
    }

    @Test
    void existingCreateGetAndListBehaviourIsUnchanged() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String body = """
                {
                  "tenantId": "%s",
                  "partyType": "PERSON",
                  "displayName": "Ada Lovelace",
                  "legalName": "Ada Lovelace"
                }
                """.formatted(tenantId);

        String created = mockMvc.perform(post("/internal/v1/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()))
                .andExpect(jsonPath("$.partyNumber").value(org.hamcrest.Matchers.startsWith("PTY-")))
                .andExpect(jsonPath("$.partyType").value("PERSON"))
                .andExpect(jsonPath("$.status").value("PROSPECT"))
                .andExpect(jsonPath("$.sanctionsStatus").value("NOT_SCREENED"))
                .andExpect(jsonPath("$.confidentiality").value("INTERNAL"))
                .andExpect(jsonPath("$.version").value(0))
                .andReturn().getResponse().getContentAsString();

        JsonNode node = json.readTree(created);
        String id = node.get("id").asText();

        mockMvc.perform(get("/internal/v1/parties/{id}", id).param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.displayName").value("Ada Lovelace"));

        mockMvc.perform(get("/internal/v1/parties").param("tenantId", tenantId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id));

        mockMvc.perform(get("/internal/v1/parties/{id}", UUID.randomUUID())
                        .param("tenantId", tenantId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void statusPatchUpdatesThePartyAndValidatesTheStatus() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String body = """
                {
                  "tenantId": "%s",
                  "partyType": "ORGANIZATION",
                  "displayName": "Analytical Engines Ltd",
                  "legalName": "Analytical Engines Limited"
                }
                """.formatted(tenantId);

        String created = mockMvc.perform(post("/internal/v1/parties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(created).get("id").asText();

        mockMvc.perform(patch("/internal/v1/parties/{id}/status", id)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\",\"reason\":\"verified\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.version").value(1));

        mockMvc.perform(patch("/internal/v1/parties/{id}/status", id)
                        .param("tenantId", tenantId.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"NOT_A_STATUS\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
