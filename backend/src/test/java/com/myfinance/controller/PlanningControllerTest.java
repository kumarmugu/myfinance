package com.myfinance.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PlanningControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser
    void shouldGetAllocation() throws Exception {
        mockMvc.perform(get("/api/planning/allocation")).andExpect(status().isOk()).andExpect(jsonPath("$.targets").exists()).andExpect(jsonPath("$.current").exists());
    }

    @Test
    @WithMockUser
    void shouldGetDeposits() throws Exception {
        mockMvc.perform(get("/api/planning/deposits")).andExpect(status().isOk());
    }
}
