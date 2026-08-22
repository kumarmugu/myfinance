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
class DashboardControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @WithMockUser
    void shouldGetDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary")).andExpect(status().isOk()).andExpect(jsonPath("$.totalNetWorth").exists());
    }

    @Test
    @WithMockUser
    void shouldGetAllocation() throws Exception {
        mockMvc.perform(get("/api/dashboard/allocation")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void shouldTakeSnapshot() throws Exception {
        mockMvc.perform(post("/api/dashboard/snapshot")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser
    void shouldGetNetWorthHistory() throws Exception {
        mockMvc.perform(get("/api/dashboard/net-worth/history")).andExpect(status().isOk());
    }
}
