package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Owner;
import com.myfinance.model.enums.OwnerRelationship;
import com.myfinance.repository.OwnerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OwnerControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private OwnerRepository ownerRepository;

    @BeforeEach
    void setup() {
        ownerRepository.deleteAll();
    }

    @Test
    @WithMockUser
    void shouldCreateOwner() throws Exception {
        Owner owner = Owner.builder().name("John").relationship(OwnerRelationship.SELF).build();

        mockMvc.perform(post("/api/owners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("John")))
                .andExpect(jsonPath("$.relationship", is("SELF")));
    }

    @Test
    @WithMockUser
    void shouldGetAllOwners() throws Exception {
        ownerRepository.save(Owner.builder().name("Alice").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
        ownerRepository.save(Owner.builder().name("Bob").relationship(OwnerRelationship.SPOUSE).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/owners"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldUpdateOwner() throws Exception {
        Owner saved = ownerRepository.save(Owner.builder().name("Alice").relationship(OwnerRelationship.SELF).build());
        Owner update = Owner.builder().name("Alice Updated").relationship(OwnerRelationship.SPOUSE).build();

        mockMvc.perform(put("/api/owners/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name", is("Alice Updated")))
                .andExpect(jsonPath("$.relationship", is("SPOUSE")));
    }

    @Test
    @WithMockUser
    void shouldSupportAllRelationships() throws Exception {
        for (OwnerRelationship rel : OwnerRelationship.values()) {
            Owner owner = Owner.builder().name("Test-" + rel.name()).relationship(rel).build();
            mockMvc.perform(post("/api/owners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(owner)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.relationship", is(rel.name())));
        }
    }

    @Test
    @WithMockUser
    void shouldDeleteOwnerWithNoReferences() throws Exception {
        Owner o = ownerRepository.save(Owner.builder().name("ToDelete").relationship(OwnerRelationship.SELF).userId(testUser.getId()).build());
        mockMvc.perform(delete("/api/owners/" + o.getId())).andExpect(status().isNoContent());
    }
}
