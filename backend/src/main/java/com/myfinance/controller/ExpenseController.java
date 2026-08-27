package com.myfinance.controller;

import com.myfinance.model.Expense;
import com.myfinance.repository.ExpenseRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
public class ExpenseController {
    private final ExpenseRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Expense> getAll(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Long categoryId) {
        Long uid = tenantContext.getCurrentUserId();
        if (year != null && month != null) {
            return repository.findByUserIdAndYearMonth(uid, year, month);
        }
        if (categoryId != null) {
            return repository.findByUserIdAndCategoryId(uid, categoryId);
        }
        return repository.findByUserIdOrderByExpenseDateDesc(uid);
    }

    @PostMapping
    public ResponseEntity<Expense> create(@RequestBody Expense expense) {
        Long uid = tenantContext.getCurrentUserId();
        expense.setUserId(uid);
        if (expense.getExpenseDate() == null) expense.setExpenseDate(LocalDate.now());
        log.info("Creating expense: date={}, amount={}, category={}", expense.getExpenseDate(), expense.getAmount(), expense.getCategory() != null ? expense.getCategory().getName() : "?");
        Expense saved = repository.save(expense);
        log.info("Created expense id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public Expense update(@PathVariable Long id, @RequestBody Expense updated) {
        log.info("Updating expense id={}", id);
        Expense existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Expense not found"));
        existing.setExpenseDate(updated.getExpenseDate());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setAmount(updated.getAmount());
        existing.setCurrency(updated.getCurrency());
        existing.setNotes(updated.getNotes());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting expense id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
