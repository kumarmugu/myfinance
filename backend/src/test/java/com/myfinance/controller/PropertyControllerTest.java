package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.Property;
import com.myfinance.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PropertyControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PropertyRepository repository;

    @BeforeEach
    void setup() { repository.deleteAll(); }

    private Property.PropertyBuilder ownedProperty() {
        return Property.builder()
                .propertyName("Condo Tampines")
                .propertyType("CONDO")
                .address("123 Tampines Ave")
                .country("Singapore")
                .purchasePrice(new BigDecimal("800000"))
                .currentValue(new BigDecimal("1000000"))
                .outstandingLoan(new BigDecimal("400000"))
                .currency("SGD")
                .purchaseDate(LocalDate.of(2020, 1, 15))
                .tenure("99 years")
                .areaSize(new BigDecimal("1200"))
                .areaUnit("sqft")
                .ownership("SOLE")
                .includeInNetWorth(true)
                .status("OWNED");
    }

    @Test
    @WithMockUser
    void shouldCreateProperty() throws Exception {
        Property property = ownedProperty().build();

        mockMvc.perform(post("/api/properties")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(property)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.propertyName", is("Condo Tampines")))
                .andExpect(jsonPath("$.propertyType", is("CONDO")))
                .andExpect(jsonPath("$.currentValue", is(1000000)));
    }

    @Test
    @WithMockUser
    void shouldListProperties() throws Exception {
        repository.save(ownedProperty().propertyName("House A").userId(testUser.getId()).build());
        repository.save(ownedProperty().propertyName("House B").userId(testUser.getId()).build());

        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldUpdateProperty() throws Exception {
        Property saved = repository.save(ownedProperty().propertyName("Old Name").userId(testUser.getId()).build());

        Property update = ownedProperty()
                .propertyName("Updated Name")
                .currentValue(new BigDecimal("1200000"))
                .status("RENTED_OUT")
                .monthlyRental(new BigDecimal("3000"))
                .build();

        mockMvc.perform(put("/api/properties/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyName", is("Updated Name")))
                .andExpect(jsonPath("$.currentValue", is(1200000)))
                .andExpect(jsonPath("$.status", is("RENTED_OUT")));
    }

    @Test
    @WithMockUser
    void shouldDeleteProperty() throws Exception {
        Property saved = repository.save(ownedProperty().userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/properties/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/properties"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldGetSummary() throws Exception {
        repository.save(ownedProperty()
                .propertyName("Owned 1")
                .currentValue(new BigDecimal("1000000"))
                .outstandingLoan(new BigDecimal("400000"))
                .status("OWNED")
                .userId(testUser.getId()).build());
        repository.save(ownedProperty()
                .propertyName("Owned 2")
                .currentValue(new BigDecimal("500000"))
                .outstandingLoan(new BigDecimal("100000"))
                .status("OWNED")
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/properties/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProperties", is(2)))
                .andExpect(jsonPath("$.totalValue", is(1500000.00)))
                .andExpect(jsonPath("$.totalLoan", is(500000.00)))
                .andExpect(jsonPath("$.totalEquity", is(1000000.00)));
    }

    @Test
    @WithMockUser
    void shouldExcludeSoldPropertyFromSummary() throws Exception {
        repository.save(ownedProperty()
                .propertyName("Owned")
                .currentValue(new BigDecimal("1000000"))
                .outstandingLoan(new BigDecimal("400000"))
                .status("OWNED")
                .userId(testUser.getId()).build());
        repository.save(ownedProperty()
                .propertyName("Sold")
                .currentValue(new BigDecimal("2000000"))
                .outstandingLoan(new BigDecimal("800000"))
                .status("SOLD")
                .userId(testUser.getId()).build());

        mockMvc.perform(get("/api/properties/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProperties", is(1)))
                .andExpect(jsonPath("$.totalValue", is(1000000.00)))
                .andExpect(jsonPath("$.totalLoan", is(400000.00)))
                .andExpect(jsonPath("$.totalEquity", is(600000.00)));
    }
}
