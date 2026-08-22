package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.WorkExperience;
import com.myfinance.repository.WorkExperienceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class WorkExperienceControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private WorkExperienceRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    @Test
    @WithMockUser
    void shouldCreateWorkExperience() throws Exception {
        WorkExperience exp = WorkExperience.builder()
                .company("BCS").position("Software Engineer").level("PM5")
                .country("Singapore").startDate(LocalDate.of(2020, 3, 1))
                .isCurrent(true).build();

        mockMvc.perform(post("/api/work-experience")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(exp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.company", is("BCS")))
                .andExpect(jsonPath("$.level", is("PM5")))
                .andExpect(jsonPath("$.isCurrent", is(true)));
    }

    @Test
    @WithMockUser
    void shouldListSortedByDate() throws Exception {
        repository.save(WorkExperience.builder().company("Old Co").startDate(LocalDate.of(2015, 1, 1)).endDate(LocalDate.of(2018, 6, 1)).userId(testUser.getId()).build());
        repository.save(WorkExperience.builder().company("New Co").startDate(LocalDate.of(2020, 1, 1)).isCurrent(true).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/work-experience"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].company", is("New Co")))
                .andExpect(jsonPath("$[1].company", is("Old Co")));
    }

    @Test
    @WithMockUser
    void shouldUpdateWorkExperience() throws Exception {
        WorkExperience exp = repository.save(WorkExperience.builder().company("Old").startDate(LocalDate.of(2020, 1, 1)).userId(testUser.getId()).build());
        WorkExperience update = WorkExperience.builder().company("New Co").position("Lead").startDate(LocalDate.of(2020, 1, 1)).build();
        mockMvc.perform(put("/api/work-experience/" + exp.getId()).contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.company", is("New Co")));
    }

    @Test
    @WithMockUser
    void shouldDeleteWorkExperience() throws Exception {
        WorkExperience exp = repository.save(WorkExperience.builder().company("Del").startDate(LocalDate.of(2019, 1, 1)).userId(testUser.getId()).build());
        mockMvc.perform(delete("/api/work-experience/" + exp.getId())).andExpect(status().isNoContent());
    }
}
