package com.myfinance.service;

import com.myfinance.model.*;
import com.myfinance.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BudgetService {

    private final BudgetPlanRepository planRepository;
    private final BudgetIncomeRepository incomeRepository;
    private final BudgetAllocationRepository allocationRepository;
    private final BudgetCategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;

    // ─── Monthly Report ───

    public Map<String, Object> getMonthlyReport(Long userId, int year, int month) {
        Map<String, Object> report = new HashMap<>();

        // Get or default budget plan
        Optional<BudgetPlan> planOpt = planRepository.findByUserIdAndYearAndMonth(userId, year, month);
        BigDecimal savingsPct = planOpt.map(BudgetPlan::getSavingsTargetPct).orElse(new BigDecimal("50.00"));

        // Planned income
        List<BudgetIncome> incomes = planOpt.map(p -> incomeRepository.findByBudgetPlanId(p.getId())).orElse(List.of());
        BigDecimal totalPlannedIncome = incomes.stream().map(BudgetIncome::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Planned expenses (allocations)
        List<BudgetAllocation> allocations = planOpt.map(p -> allocationRepository.findByBudgetPlanId(p.getId())).orElse(List.of());
        BigDecimal totalPlannedExpense = allocations.stream().map(BudgetAllocation::getPlannedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Savings calculations
        BigDecimal targetSavings = totalPlannedIncome.multiply(savingsPct).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal availableForExpenses = totalPlannedIncome.subtract(targetSavings);
        boolean isOverBudget = totalPlannedExpense.compareTo(availableForExpenses) > 0;
        BigDecimal excess = isOverBudget ? totalPlannedExpense.subtract(availableForExpenses) : BigDecimal.ZERO;

        // Actual expenses for this month
        List<Object[]> actuals = expenseRepository.sumByCategoryForMonth(userId, year, month);
        Map<Long, BigDecimal> actualByCategory = actuals.stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (BigDecimal) r[1]));
        BigDecimal totalActualExpense = actualByCategory.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Category comparison
        List<Map<String, Object>> categoryComparison = allocations.stream().map(a -> {
            Map<String, Object> item = new HashMap<>();
            item.put("categoryId", a.getCategory().getId());
            item.put("categoryName", a.getCategory().getName());
            item.put("planned", a.getPlannedAmount());
            BigDecimal actual = actualByCategory.getOrDefault(a.getCategory().getId(), BigDecimal.ZERO);
            item.put("actual", actual);
            item.put("variance", actual.subtract(a.getPlannedAmount()));
            String status = actual.compareTo(a.getPlannedAmount()) > 0 ? "OVER" : actual.compareTo(a.getPlannedAmount()) == 0 ? "ON_TRACK" : "UNDER";
            item.put("status", status);
            return item;
        }).collect(Collectors.toList());

        // Actual savings
        BigDecimal actualSavings = totalPlannedIncome.subtract(totalActualExpense);

        report.put("year", year);
        report.put("month", month);
        report.put("savingsTargetPct", savingsPct);
        report.put("totalPlannedIncome", totalPlannedIncome);
        report.put("targetSavings", targetSavings);
        report.put("availableForExpenses", availableForExpenses);
        report.put("totalPlannedExpense", totalPlannedExpense);
        report.put("totalActualExpense", totalActualExpense);
        report.put("actualSavings", actualSavings);
        report.put("isOverBudget", isOverBudget);
        report.put("excess", excess);
        report.put("incomes", incomes);
        report.put("allocations", allocations);
        report.put("categories", categoryComparison);
        report.put("hasPlan", planOpt.isPresent());

        return report;
    }

    // ─── Annual Report ───

    public Map<String, Object> getAnnualReport(Long userId, int year) {
        List<BudgetPlan> plans = planRepository.findByUserIdAndYearOrderByMonthAsc(userId, year);

        List<Map<String, Object>> months = new ArrayList<>();
        BigDecimal annualIncome = BigDecimal.ZERO;
        BigDecimal annualPlannedExpense = BigDecimal.ZERO;
        BigDecimal annualActualExpense = BigDecimal.ZERO;

        for (int m = 1; m <= 12; m++) {
            final int month = m;
            Optional<BudgetPlan> plan = plans.stream().filter(p -> p.getMonth() == month).findFirst();
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal planned = BigDecimal.ZERO;
            BigDecimal actual = BigDecimal.ZERO;

            if (plan.isPresent()) {
                income = incomeRepository.findByBudgetPlanId(plan.get().getId()).stream()
                        .map(BudgetIncome::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                planned = allocationRepository.findByBudgetPlanId(plan.get().getId()).stream()
                        .map(BudgetAllocation::getPlannedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            }

            List<Object[]> actuals = expenseRepository.sumByCategoryForMonth(userId, year, month);
            actual = actuals.stream().map(r -> (BigDecimal) r[1]).reduce(BigDecimal.ZERO, BigDecimal::add);

            annualIncome = annualIncome.add(income);
            annualPlannedExpense = annualPlannedExpense.add(planned);
            annualActualExpense = annualActualExpense.add(actual);

            Map<String, Object> monthData = new HashMap<>();
            monthData.put("month", month);
            monthData.put("income", income);
            monthData.put("plannedExpense", planned);
            monthData.put("actualExpense", actual);
            monthData.put("plannedSavings", income.subtract(planned));
            monthData.put("actualSavings", income.subtract(actual));
            monthData.put("hasPlan", plan.isPresent());
            months.add(monthData);
        }

        Map<String, Object> report = new HashMap<>();
        report.put("year", year);
        report.put("months", months);
        report.put("annualIncome", annualIncome);
        report.put("annualPlannedExpense", annualPlannedExpense);
        report.put("annualActualExpense", annualActualExpense);
        report.put("annualPlannedSavings", annualIncome.subtract(annualPlannedExpense));
        report.put("annualActualSavings", annualIncome.subtract(annualActualExpense));
        return report;
    }

    // ─── Initialize default categories for a new user ───

    public void initializeDefaultCategories(Long userId) {
        if (!categoryRepository.findByUserIdOrderBySortOrderAsc(userId).isEmpty()) return;

        String[][] defaults = {
            {"Groceries", "Essential", "1"},
            {"Rent / Housing", "Essential", "2"},
            {"Utilities", "Essential", "3"},
            {"Transportation", "Essential", "4"},
            {"Insurance", "Essential", "5"},
            {"Taxes", "Essential", "6"},
            {"Entertainment", "Lifestyle", "7"},
            {"Dining", "Lifestyle", "8"},
            {"Shopping", "Lifestyle", "9"},
            {"Education", "Education", "10"},
            {"Courses / Books", "Education", "11"},
            {"Child Education", "Family", "12"},
            {"Childcare", "Family", "13"},
            {"Medical / Health", "Family", "14"},
            {"Vacation / Travel", "Special", "15"},
            {"Gifts / Celebrations", "Special", "16"},
            {"Other", "Other", "99"},
        };

        for (String[] cat : defaults) {
            categoryRepository.save(BudgetCategory.builder()
                    .userId(userId).name(cat[0]).parentCategory(cat[1])
                    .sortOrder(Integer.parseInt(cat[2])).build());
        }
        log.info("Initialized default budget categories for userId={}", userId);
    }
}
