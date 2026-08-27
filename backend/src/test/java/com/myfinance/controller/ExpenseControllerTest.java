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
import java.time.LocalDate;
import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ExpenseControllerTest extends BaseControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private BudgetCategoryRepository categoryRepository;
    @Autowired private BudgetPlanRepository planRepository;
    @Autowired private BudgetIncomeRepository incomeRepository;
    @Autowired private BudgetAllocationRepository allocationRepository;

    private BudgetCategory testCategory;

    @BeforeEach
    void setup() {
        expenseRepository.deleteAll();
        allocationRepository.deleteAll();
        incomeRepository.deleteAll();
        planRepository.deleteAll();
        categoryRepository.deleteAll();

        testCategory = categoryRepository.save(BudgetCategory.builder()
                .name("Groceries").parentCategory("Essential").sortOrder(1).userId(testUser.getId()).build());
    }

    @Test
    @WithMockUser
    void shouldCreateExpense() throws Exception {
        Map<String, Object> expense = Map.of(
                "expenseDate", "2026-08-15",
                "description", "Supermarket shopping",
                "category", Map.of("id", testCategory.getId()),
                "amount", "125.50"
        );

        mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(expense)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description", is("Supermarket shopping")))
                .andExpect(jsonPath("$.amount", is(125.50)))
                .andExpect(jsonPath("$.expenseDate", is("2026-08-15")))
                .andExpect(jsonPath("$.category.id", is(testCategory.getId().intValue())));
    }

    @Test
    @WithMockUser
    void shouldListAllExpenses() throws Exception {
        expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 8, 10)).description("Lunch")
                .category(testCategory).amount(new BigDecimal("15.00")).userId(testUser.getId()).build());
        expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 8, 12)).description("Dinner")
                .category(testCategory).amount(new BigDecimal("45.00")).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser
    void shouldFilterByYearAndMonth() throws Exception {
        expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 8, 10)).description("August expense")
                .category(testCategory).amount(new BigDecimal("100.00")).userId(testUser.getId()).build());
        expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 7, 5)).description("July expense")
                .category(testCategory).amount(new BigDecimal("200.00")).userId(testUser.getId()).build());

        mockMvc.perform(get("/api/expenses?year=2026&month=8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].description", is("August expense")));
    }

    @Test
    @WithMockUser
    void shouldUpdateExpense() throws Exception {
        Expense saved = expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 8, 10)).description("Old desc")
                .category(testCategory).amount(new BigDecimal("50.00")).userId(testUser.getId()).build());

        Map<String, Object> updated = Map.of(
                "expenseDate", "2026-08-11",
                "description", "Updated desc",
                "category", Map.of("id", testCategory.getId()),
                "amount", "75.00"
        );

        mockMvc.perform(put("/api/expenses/" + saved.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updated)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Updated desc")))
                .andExpect(jsonPath("$.amount", is(75.00)))
                .andExpect(jsonPath("$.expenseDate", is("2026-08-11")));
    }

    @Test
    @WithMockUser
    void shouldDeleteExpense() throws Exception {
        Expense saved = expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 8, 10)).description("To delete")
                .category(testCategory).amount(new BigDecimal("30.00")).userId(testUser.getId()).build());

        mockMvc.perform(delete("/api/expenses/" + saved.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser
    void shouldAppearInMonthlyReportCategoryTotals() throws Exception {
        // Setup a budget plan with allocation
        BudgetPlan plan = planRepository.save(BudgetPlan.builder()
                .year(2026).month(8).savingsTargetPct(new BigDecimal("50.00")).userId(testUser.getId()).build());
        incomeRepository.save(BudgetIncome.builder()
                .budgetPlan(plan).source("Salary").amount(new BigDecimal("10000")).userId(testUser.getId()).build());
        allocationRepository.save(BudgetAllocation.builder()
                .budgetPlan(plan).category(testCategory).plannedAmount(new BigDecimal("500")).userId(testUser.getId()).build());

        // Add actual expenses for August
        expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 8, 5)).description("Groceries week 1")
                .category(testCategory).amount(new BigDecimal("120.00")).userId(testUser.getId()).build());
        expenseRepository.save(Expense.builder()
                .expenseDate(LocalDate.of(2026, 8, 12)).description("Groceries week 2")
                .category(testCategory).amount(new BigDecimal("95.00")).userId(testUser.getId()).build());

        // Verify report reflects actual expense totals
        mockMvc.perform(get("/api/budget/report/2026/8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActualExpense", is(215.00)))
                .andExpect(jsonPath("$.categories[0].categoryName", is("Groceries")))
                .andExpect(jsonPath("$.categories[0].planned", is(500.00)))
                .andExpect(jsonPath("$.categories[0].actual", is(215.00)))
                .andExpect(jsonPath("$.categories[0].variance", is(-285.00)))
                .andExpect(jsonPath("$.categories[0].status", is("UNDER")));
    }
}
