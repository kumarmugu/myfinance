package com.myfinance.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myfinance.model.*;
import com.myfinance.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class BudgetControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BudgetCategoryRepository categoryRepository;
    @Autowired private BudgetPlanRepository planRepository;
    @Autowired private BudgetIncomeRepository incomeRepository;
    @Autowired private BudgetAllocationRepository allocationRepository;
    @Autowired private ExpenseRepository expenseRepository;

    @BeforeEach
    void setup() {
        expenseRepository.deleteAll();
        allocationRepository.deleteAll();
        incomeRepository.deleteAll();
        planRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    // ─── Categories ───

    @Test
    @WithMockUser
    void shouldReturnDefaultCategories() throws Exception {
        // GET /categories auto-initializes defaults when none exist
        mockMvc.perform(get("/api/budget/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(17))))
                .andExpect(jsonPath("$[0].name", is("Groceries")))
                .andExpect(jsonPath("$[0].parentCategory", is("Essential")));
    }

    @Test
    @WithMockUser
    void shouldCreateCustomCategory() throws Exception {
        BudgetCategory category = BudgetCategory.builder()
                .name("Subscriptions")
                .parentCategory("Lifestyle")
                .sortOrder(20)
                .build();

        mockMvc.perform(post("/api/budget/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(category)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name", is("Subscriptions")))
                .andExpect(jsonPath("$.parentCategory", is("Lifestyle")))
                .andExpect(jsonPath("$.sortOrder", is(20)));
    }

    @Test
    @WithMockUser
    void shouldRejectDuplicateCategoryName() throws Exception {
        categoryRepository.save(BudgetCategory.builder()
                .name("Groceries").parentCategory("Essential").sortOrder(1).userId(testUser.getId()).build());

        BudgetCategory duplicate = BudgetCategory.builder()
                .name("Groceries")
                .parentCategory("Essential")
                .sortOrder(2)
                .build();

        mockMvc.perform(post("/api/budget/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(duplicate)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Category already exists")));
    }

    @Test
    @WithMockUser
    void shouldSoftDeleteCategory() throws Exception {
        BudgetCategory cat = categoryRepository.save(BudgetCategory.builder()
                .name("ToDelete").parentCategory("Other").sortOrder(50).userId(testUser.getId()).isActive(true).build());

        mockMvc.perform(delete("/api/budget/categories/" + cat.getId()))
                .andExpect(status().isNoContent());

        // Verify soft delete — isActive should be false
        BudgetCategory deleted = categoryRepository.findById(cat.getId()).orElseThrow();
        assert !deleted.getIsActive();
    }

    // ─── Plans ───

    @Test
    @WithMockUser
    void shouldCreateBudgetPlan() throws Exception {
        BudgetPlan plan = BudgetPlan.builder()
                .year(2026).month(8)
                .savingsTargetPct(new BigDecimal("40.00"))
                .build();

        mockMvc.perform(post("/api/budget/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(plan)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(8)))
                .andExpect(jsonPath("$.savingsTargetPct", is(40.00)));
    }

    @Test
    @WithMockUser
    void shouldUpdateExistingPlan() throws Exception {
        // Create first
        BudgetPlan plan = BudgetPlan.builder()
                .year(2026).month(8)
                .savingsTargetPct(new BigDecimal("40.00"))
                .build();

        mockMvc.perform(post("/api/budget/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(plan)))
                .andExpect(status().isCreated());

        // Post same year/month should update
        BudgetPlan updated = BudgetPlan.builder()
                .year(2026).month(8)
                .savingsTargetPct(new BigDecimal("60.00"))
                .build();

        mockMvc.perform(post("/api/budget/plans")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsTargetPct", is(60.00)));
    }

    @Test
    @WithMockUser
    void shouldGetSpecificPlan() throws Exception {
        planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/budget/plans/2026/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(8)));
    }

    // ─── Income ───

    @Test
    @WithMockUser
    void shouldAddIncomeEntry() throws Exception {
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());

        Map<String, Object> income = Map.of(
                "budgetPlan", Map.of("id", plan.getId()),
                "source", "Salary",
                "amount", "14000.00"
        );

        mockMvc.perform(post("/api/budget/income")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(income)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source", is("Salary")))
                .andExpect(jsonPath("$.amount", is(14000.00)));
    }

    @Test
    @WithMockUser
    void shouldListIncomesByPlan() throws Exception {
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());

        incomeRepository.save(BudgetIncome.builder().budgetPlan(plan).source("Salary").amount(new BigDecimal("14000")).userId(testUser.getId()).build());
        incomeRepository.save(BudgetIncome.builder().budgetPlan(plan).source("Dividends").amount(new BigDecimal("500")).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/budget/income/" + plan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.source=='Salary')].amount", contains(closeTo(14000.0, 0.01))))
                .andExpect(jsonPath("$[?(@.source=='Dividends')].amount", contains(closeTo(500.0, 0.01))));
    }

    @Test
    @WithMockUser
    void shouldDeleteIncome() throws Exception {
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());
        BudgetIncome income = incomeRepository.save(BudgetIncome.builder()
                .budgetPlan(plan).source("Bonus").amount(new BigDecimal("5000")).userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/budget/income/" + income.getId()))
                .andExpect(status().isNoContent());
    }

    // ─── Allocations ───

    @Test
    @WithMockUser
    void shouldAddAllocation() throws Exception {
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());
        BudgetCategory cat = categoryRepository.save(BudgetCategory.builder()
                .name("Groceries").parentCategory("Essential").sortOrder(1).userId(testUser.getId()).build());

        Map<String, Object> allocation = Map.of(
                "budgetPlan", Map.of("id", plan.getId()),
                "category", Map.of("id", cat.getId()),
                "plannedAmount", "800.00"
        );

        mockMvc.perform(post("/api/budget/allocations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(allocation)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plannedAmount", is(800.00)));
    }

    @Test
    @WithMockUser
    void shouldListAllocationsByPlan() throws Exception {
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());
        BudgetCategory cat1 = categoryRepository.save(BudgetCategory.builder()
                .name("Groceries").parentCategory("Essential").sortOrder(1).userId(testUser.getId()).build());
        BudgetCategory cat2 = categoryRepository.save(BudgetCategory.builder()
                .name("Rent").parentCategory("Essential").sortOrder(2).userId(testUser.getId()).build());

        allocationRepository.save(BudgetAllocation.builder().budgetPlan(plan).category(cat1).plannedAmount(new BigDecimal("800")).userId(testUser.getId()).build());
        allocationRepository.save(BudgetAllocation.builder().budgetPlan(plan).category(cat2).plannedAmount(new BigDecimal("2000")).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/budget/allocations/" + plan.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldDeleteAllocation() throws Exception {
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());
        BudgetCategory cat = categoryRepository.save(BudgetCategory.builder()
                .name("Groceries").parentCategory("Essential").sortOrder(1).userId(testUser.getId()).build());
        BudgetAllocation alloc = allocationRepository.save(BudgetAllocation.builder()
                .budgetPlan(plan).category(cat).plannedAmount(new BigDecimal("800")).userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/budget/allocations/" + alloc.getId()))
                .andExpect(status().isNoContent());
    }

    // ─── Report ───

    @Test
    @WithMockUser
    void shouldReturnMonthlyReport() throws Exception {
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());
        BudgetCategory cat = categoryRepository.save(BudgetCategory.builder()
                .name("Groceries").parentCategory("Essential").sortOrder(1).userId(testUser.getId()).build());

        incomeRepository.save(BudgetIncome.builder().budgetPlan(plan).source("Salary").amount(new BigDecimal("10000")).userId(testUser.getId()).build());
        allocationRepository.save(BudgetAllocation.builder().budgetPlan(plan).category(cat).plannedAmount(new BigDecimal("800")).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/budget/report/2026/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year", is(2026)))
                .andExpect(jsonPath("$.month", is(8)))
                .andExpect(jsonPath("$.totalPlannedIncome", is(10000.00)))
                .andExpect(jsonPath("$.savingsTargetPct", is(50.00)))
                .andExpect(jsonPath("$.targetSavings", is(5000.00)))
                .andExpect(jsonPath("$.availableForExpenses", is(5000.00)))
                .andExpect(jsonPath("$.totalPlannedExpense", is(800.00)))
                .andExpect(jsonPath("$.totalActualExpense", is(0)))
                .andExpect(jsonPath("$.hasPlan", is(true)))
                .andExpect(jsonPath("$.categories", hasSize(1)))
                .andExpect(jsonPath("$.categories[0].categoryName", is("Groceries")))
                .andExpect(jsonPath("$.categories[0].planned", is(800.00)));
    }
}
