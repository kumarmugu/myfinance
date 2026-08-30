package com.myfinance.controller;

import com.myfinance.model.SalaryRecord;
import com.myfinance.repository.SalaryRecordRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/salary")
@RequiredArgsConstructor
@Slf4j
public class SalaryController {
    private final SalaryRecordRepository repository;
    private final TenantContext tenantContext;
    private final com.myfinance.service.CurrencyConversionService fx;

    @GetMapping
    public List<SalaryRecord> getAll(@RequestParam(required = false) Integer year, @RequestParam(required = false) String country) {
        Long uid = tenantContext.getCurrentUserId();
        if (year != null) return repository.findByUserIdAndYearOrderByMonthAsc(uid, year);
        if (country != null) return repository.findByUserIdAndCountryOrderByYearDescMonthDesc(uid, country);
        return repository.findByUserIdOrderByYearDescMonthDesc(uid);
    }

    /**
     * Summary consolidated in the user's base currency. All amounts are FX-converted per
     * record from their original currency (records may be in different currencies), so the
     * grand total and yearly figures are correct across currencies. Original values are
     * never mutated. Includes a per-year table and monthly-average series for charting.
     */
    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Long uid = tenantContext.getCurrentUserId();
        List<SalaryRecord> all = repository.findByUserIdOrderByYearDescMonthDesc(uid);

        // Per-year aggregation in base currency.
        Map<Integer, BigDecimal> yearTotal = new TreeMap<>();
        Map<Integer, BigDecimal> yearSalary = new TreeMap<>();   // non-bonus only
        Map<Integer, BigDecimal> yearBonus = new TreeMap<>();
        Map<Integer, Integer> yearSalaryMonths = new TreeMap<>();

        for (SalaryRecord s : all) {
            int y = s.getYear() != null ? s.getYear() : 0;
            BigDecimal base = fx.toBase(s.getAmount(), s.getCurrency(), uid);
            yearTotal.merge(y, base, BigDecimal::add);
            if (Boolean.TRUE.equals(s.getIsBonus())) {
                yearBonus.merge(y, base, BigDecimal::add);
            } else {
                yearSalary.merge(y, base, BigDecimal::add);
                yearSalaryMonths.merge(y, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> yearly = new ArrayList<>();
        List<Map<String, Object>> monthlyAvgSeries = new ArrayList<>(); // for the growth chart
        for (Integer y : yearTotal.keySet()) {
            BigDecimal salary = yearSalary.getOrDefault(y, BigDecimal.ZERO);
            int months = yearSalaryMonths.getOrDefault(y, 0);
            BigDecimal monthlyAvg = months > 0
                    ? salary.divide(BigDecimal.valueOf(months), 2, java.math.RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("year", y);
            row.put("total", yearTotal.get(y));
            row.put("salaryTotal", salary);
            row.put("bonusTotal", yearBonus.getOrDefault(y, BigDecimal.ZERO));
            row.put("months", months);
            row.put("monthlyAvg", monthlyAvg);
            yearly.add(row);

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("year", y);
            point.put("monthlyAvg", monthlyAvg);
            monthlyAvgSeries.add(point);
        }

        BigDecimal grandTotal = yearTotal.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Display-currency factors from the user's own rates (mirrors the dashboard/bank-savings pattern).
        String baseCurrency = fx.getBaseCurrency(uid);
        Map<String, BigDecimal> displayRates = new LinkedHashMap<>();
        for (String code : fx.getDisplayCurrencies(uid)) {
            BigDecimal factor = fx.factorFromBase(code, uid);
            if (factor != null) displayRates.put(code, factor);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("yearly", yearly);
        result.put("monthlyAvgSeries", monthlyAvgSeries);
        result.put("grandTotal", grandTotal);
        result.put("years", yearTotal.size());
        result.put("baseCurrency", baseCurrency);
        result.put("displayRates", displayRates);
        return result;
    }

    @GetMapping("/years")
    public List<Integer> getAvailableYears() {
        Long uid = tenantContext.getCurrentUserId();
        return repository.findByUserIdOrderByYearDescMonthDesc(uid).stream()
                .map(SalaryRecord::getYear)
                .distinct()
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<SalaryRecord> create(@RequestBody SalaryRecord record) {
        log.info("Creating salary record: year={}, month={}, company={}", record.getYear(), record.getMonth(), record.getCompany());
        record.setUserId(tenantContext.getCurrentUserId());
        SalaryRecord saved = repository.save(record);
        log.info("Created salary record id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public SalaryRecord update(@PathVariable Long id, @RequestBody SalaryRecord updated) {
        log.info("Updating salary record id={}", id);
        SalaryRecord existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setYear(updated.getYear());
        existing.setMonth(updated.getMonth());
        existing.setCompany(updated.getCompany());
        existing.setAmount(updated.getAmount());
        existing.setCurrency(updated.getCurrency());
        existing.setBasic(updated.getBasic());
        existing.setAllowance(updated.getAllowance());
        existing.setMobile(updated.getMobile());
        existing.setSupport(updated.getSupport());
        existing.setWeekend(updated.getWeekend());
        existing.setMealAllowance(updated.getMealAllowance());
        existing.setDeductions(updated.getDeductions());
        existing.setEmployeeContribution(updated.getEmployeeContribution());
        existing.setEmployerContribution(updated.getEmployerContribution());
        existing.setCpfEmployee(updated.getCpfEmployee());
        existing.setCpfEmployer(updated.getCpfEmployer());
        existing.setEpfEmployee(updated.getEpfEmployee());
        existing.setEpfEmployer(updated.getEpfEmployer());
        existing.setEtfEmployer(updated.getEtfEmployer());
        existing.setContributionScheme(updated.getContributionScheme());
        existing.setIsBonus(updated.getIsBonus());
        existing.setBonusMonths(updated.getBonusMonths());
        existing.setCountry(updated.getCountry());
        existing.setNotes(updated.getNotes());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting salary record id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
