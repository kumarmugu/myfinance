package com.myfinance.controller;

import com.myfinance.model.RetirementFundEntry;
import com.myfinance.repository.RetirementFundEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/retirement-fund")
@RequiredArgsConstructor
public class RetirementFundController {
    private final RetirementFundEntryRepository repository;

    @GetMapping
    public List<RetirementFundEntry> getAll(
            @RequestParam(required = false) String fundType,
            @RequestParam(required = false) Long ownerId) {
        if (fundType != null) return repository.findByFundTypeOrderByEntryDateDesc(fundType);
        if (ownerId != null) return repository.findByOwnerIdOrderByEntryDateDesc(ownerId);
        return repository.findAllByOrderByEntryDateDesc();
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        List<Object[]> data = repository.summaryByFundAndType();
        Map<String, Map<String, BigDecimal>> summary = new HashMap<>();

        for (Object[] row : data) {
            String fund = (String) row[0];
            String type = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];
            summary.computeIfAbsent(fund, k -> new HashMap<>()).put(type, amount);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("byFund", summary);
        result.put("totalEntries", repository.count());
        return result;
    }

    @PostMapping
    public ResponseEntity<RetirementFundEntry> create(@RequestBody RetirementFundEntry entry) {
        if (entry.getYear() == null && entry.getEntryDate() != null) {
            entry.setYear(entry.getEntryDate().getYear());
            entry.setMonth(entry.getEntryDate().getMonthValue());
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(entry));
    }

    @PutMapping("/{id}")
    public RetirementFundEntry update(@PathVariable Long id, @RequestBody RetirementFundEntry updated) {
        RetirementFundEntry existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setFundType(updated.getFundType());
        existing.setEntryType(updated.getEntryType());
        existing.setAmount(updated.getAmount());
        existing.setEntryDate(updated.getEntryDate());
        existing.setYear(updated.getYear());
        existing.setMonth(updated.getMonth());
        existing.setAccount(updated.getAccount());
        existing.setBalance(updated.getBalance());
        existing.setEmployer(updated.getEmployer());
        existing.setNotes(updated.getNotes());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
