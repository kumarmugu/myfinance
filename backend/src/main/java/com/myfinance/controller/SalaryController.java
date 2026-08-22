package com.myfinance.controller;

import com.myfinance.model.SalaryRecord;
import com.myfinance.repository.SalaryRecordRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/salary")
@RequiredArgsConstructor
public class SalaryController {
    private final SalaryRecordRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<SalaryRecord> getAll(@RequestParam(required = false) Integer year, @RequestParam(required = false) String country) {
        Long uid = tenantContext.getCurrentUserId();
        if (year != null) return repository.findByYearOrderByMonthAsc(year);
        if (country != null) return repository.findByCountryOrderByYearDescMonthDesc(country);
        return repository.findByUserIdOrderByYearDescMonthDesc(uid);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        List<Object[]> yearlyTotals = repository.sumByYear();
        List<Object[]> bonusTotals = repository.bonusByYear();

        List<Map<String, Object>> yearly = yearlyTotals.stream().map(row -> {
            Map<String, Object> m = new HashMap<>();
            m.put("year", row[0]);
            m.put("total", row[1]);
            return m;
        }).collect(Collectors.toList());

        Map<Integer, BigDecimal> bonusMap = bonusTotals.stream()
                .collect(Collectors.toMap(r -> (Integer) r[0], r -> (BigDecimal) r[1]));

        BigDecimal grandTotal = yearlyTotals.stream()
                .map(r -> (BigDecimal) r[1])
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("yearly", yearly);
        result.put("bonusByYear", bonusMap);
        result.put("grandTotal", grandTotal);
        result.put("years", yearlyTotals.size());
        return result;
    }

    @GetMapping("/years")
    public List<Integer> getAvailableYears() {
        return repository.findAllByOrderByYearDescMonthDesc().stream()
                .map(SalaryRecord::getYear)
                .distinct()
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<SalaryRecord> create(@RequestBody SalaryRecord record) {
        record.setUserId(tenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(record));
    }

    @PutMapping("/{id}")
    public SalaryRecord update(@PathVariable Long id, @RequestBody SalaryRecord updated) {
        SalaryRecord existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setYear(updated.getYear());
        existing.setMonth(updated.getMonth());
        existing.setCompany(updated.getCompany());
        existing.setAmount(updated.getAmount());
        existing.setBasic(updated.getBasic());
        existing.setAllowance(updated.getAllowance());
        existing.setMobile(updated.getMobile());
        existing.setSupport(updated.getSupport());
        existing.setWeekend(updated.getWeekend());
        existing.setMealAllowance(updated.getMealAllowance());
        existing.setDeductions(updated.getDeductions());
        existing.setIsBonus(updated.getIsBonus());
        existing.setBonusMonths(updated.getBonusMonths());
        existing.setCountry(updated.getCountry());
        existing.setNotes(updated.getNotes());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
