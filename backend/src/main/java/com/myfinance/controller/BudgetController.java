package com.myfinance.controller;

import com.myfinance.model.*;
import com.myfinance.repository.*;
import com.myfinance.security.TenantContext;
import com.myfinance.service.BudgetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/budget")
@RequiredArgsConstructor
@Slf4j
public class BudgetController {
    private final BudgetPlanRepository planRepository;
    private final BudgetIncomeRepository incomeRepository;
    private final BudgetAllocationRepository allocationRepository;
    private final BudgetCategoryRepository categoryRepository;
    private final BudgetService budgetService;
    private final TenantContext tenantContext;

    // ─── Categories ───

    @GetMapping("/categories")
    public List<BudgetCategory> getCategories() {
        Long uid = tenantContext.getCurrentUserId();
        List<BudgetCategory> categories = categoryRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(uid);
        if (categories.isEmpty()) {
            budgetService.initializeDefaultCategories(uid);
            categories = categoryRepository.findByUserIdAndIsActiveTrueOrderBySortOrderAsc(uid);
        }
        return categories;
    }

    @PostMapping("/categories")
    public ResponseEntity<?> createCategory(@RequestBody BudgetCategory category) {
        Long uid = tenantContext.getCurrentUserId();
        if (categoryRepository.existsByUserIdAndName(uid, category.getName())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Category already exists"));
        }
        category.setUserId(uid);
        log.info("Creating budget category: name={}", category.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(categoryRepository.save(category));
    }

    @PutMapping("/categories/{id}")
    public BudgetCategory updateCategory(@PathVariable Long id, @RequestBody BudgetCategory updated) {
        BudgetCategory existing = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        existing.setName(updated.getName());
        existing.setParentCategory(updated.getParentCategory());
        existing.setSortOrder(updated.getSortOrder());
        existing.setIsActive(updated.getIsActive());
        return categoryRepository.save(existing);
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        BudgetCategory cat = categoryRepository.findById(id).orElseThrow(() -> new RuntimeException("Category not found"));
        cat.setIsActive(false); // soft delete
        categoryRepository.save(cat);
        return ResponseEntity.noContent().build();
    }

    // ─── Budget Plans ───

    @GetMapping("/plans")
    public List<BudgetPlan> getPlans(@RequestParam(required = false) Integer year) {
        Long uid = tenantContext.getCurrentUserId();
        if (year != null) return planRepository.findByUserIdAndYearOrderByMonthAsc(uid, year);
        return planRepository.findByUserIdOrderByYearDescMonthDesc(uid);
    }

    @GetMapping("/plans/{year}/{month}")
    public ResponseEntity<?> getPlan(@PathVariable int year, @PathVariable int month) {
        Long uid = tenantContext.getCurrentUserId();
        return planRepository.findByUserIdAndYearAndMonth(uid, year, month)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/plans")
    public ResponseEntity<?> createOrUpdatePlan(@RequestBody BudgetPlan plan) {
        Long uid = tenantContext.getCurrentUserId();
        var existing = planRepository.findByUserIdAndYearAndMonth(uid, plan.getYear(), plan.getMonth());
        if (existing.isPresent()) {
            BudgetPlan p = existing.get();
            p.setSavingsTargetPct(plan.getSavingsTargetPct());
            p.setNotes(plan.getNotes());
            log.info("Updating budget plan year={} month={}", plan.getYear(), plan.getMonth());
            return ResponseEntity.ok(planRepository.save(p));
        }
        plan.setUserId(uid);
        log.info("Creating budget plan year={} month={}", plan.getYear(), plan.getMonth());
        return ResponseEntity.status(HttpStatus.CREATED).body(planRepository.save(plan));
    }

    // ─── Income ───

    @GetMapping("/income/{planId}")
    public List<BudgetIncome> getIncomes(@PathVariable Long planId) {
        return incomeRepository.findByBudgetPlanId(planId);
    }

    @PostMapping("/income")
    public ResponseEntity<BudgetIncome> createIncome(@RequestBody BudgetIncome income) {
        Long uid = tenantContext.getCurrentUserId();
        income.setUserId(uid);
        log.info("Creating budget income: source={}, amount={}", income.getSource(), income.getAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(incomeRepository.save(income));
    }

    @PutMapping("/income/{id}")
    public BudgetIncome updateIncome(@PathVariable Long id, @RequestBody BudgetIncome updated) {
        BudgetIncome existing = incomeRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setSource(updated.getSource());
        existing.setAmount(updated.getAmount());
        existing.setNotes(updated.getNotes());
        return incomeRepository.save(existing);
    }

    @DeleteMapping("/income/{id}")
    public ResponseEntity<Void> deleteIncome(@PathVariable Long id) {
        incomeRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Allocations ───

    @GetMapping("/allocations/{planId}")
    public List<BudgetAllocation> getAllocations(@PathVariable Long planId) {
        return allocationRepository.findByBudgetPlanId(planId);
    }

    @PostMapping("/allocations")
    public ResponseEntity<BudgetAllocation> createAllocation(@RequestBody BudgetAllocation allocation) {
        Long uid = tenantContext.getCurrentUserId();
        allocation.setUserId(uid);
        log.info("Creating budget allocation: categoryId={}, amount={}", allocation.getCategory().getId(), allocation.getPlannedAmount());
        return ResponseEntity.status(HttpStatus.CREATED).body(allocationRepository.save(allocation));
    }

    @PutMapping("/allocations/{id}")
    public BudgetAllocation updateAllocation(@PathVariable Long id, @RequestBody BudgetAllocation updated) {
        BudgetAllocation existing = allocationRepository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setPlannedAmount(updated.getPlannedAmount());
        existing.setCategory(updated.getCategory());
        return allocationRepository.save(existing);
    }

    @DeleteMapping("/allocations/{id}")
    public ResponseEntity<Void> deleteAllocation(@PathVariable Long id) {
        allocationRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Reports ───

    @GetMapping("/report/{year}/{month}")
    public Map<String, Object> getMonthlyReport(@PathVariable int year, @PathVariable int month) {
        Long uid = tenantContext.getCurrentUserId();
        return budgetService.getMonthlyReport(uid, year, month);
    }

    @GetMapping("/report/{year}")
    public Map<String, Object> getAnnualReport(@PathVariable int year) {
        Long uid = tenantContext.getCurrentUserId();
        return budgetService.getAnnualReport(uid, year);
    }
}
