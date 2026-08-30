package com.myfinance.controller;

import com.myfinance.model.TaxRecord;
import com.myfinance.repository.TaxRecordRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tax")
@RequiredArgsConstructor
@Slf4j
public class TaxController {
    private final TaxRecordRepository repository;
    private final TenantContext tenantContext;
    private final com.myfinance.service.CurrencyConversionService fx;

    @GetMapping
    public List<TaxRecord> getAll(@RequestParam(required = false) Long ownerId, @RequestParam(required = false) String country) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return repository.findByOwnerIdOrderByAssessmentYearDesc(ownerId);
        if (country != null) return repository.findByCountryOrderByAssessmentYearDesc(country);
        return repository.findByUserIdOrderByAssessmentYearDesc(uid);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Long uid = tenantContext.getCurrentUserId();
        List<TaxRecord> all = repository.findByUserIdOrderByAssessmentYearDesc(uid);
        // Consolidated in the user's base currency, converting each record's original currency.
        BigDecimal totalPaid = all.stream()
                .map(t -> fx.toBase(t.getTaxPayable(), t.getCurrency(), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIncome = all.stream()
                .map(t -> fx.toBase(t.getEmployment(), t.getCurrency(), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("totalTaxPaid", totalPaid);
        result.put("totalIncome", totalIncome);
        result.put("years", all.size());
        result.put("baseCurrency", fx.getBaseCurrency(uid));
        return result;
    }

    @GetMapping("/{id}")
    public TaxRecord getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Tax record not found"));
    }

    @PostMapping
    public ResponseEntity<TaxRecord> create(@RequestBody TaxRecord record) {
        log.info("Creating tax record: assessmentYear={}, country={}", record.getAssessmentYear(), record.getCountry());
        record.setUserId(tenantContext.getCurrentUserId());
        TaxRecord saved = repository.save(record);
        log.info("Created tax record id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public TaxRecord update(@PathVariable Long id, @RequestBody TaxRecord updated) {
        log.info("Updating tax record id={}", id);
        TaxRecord existing = getById(id);
        existing.setAssessmentYear(updated.getAssessmentYear());
        existing.setCurrency(updated.getCurrency());
        existing.setEmployment(updated.getEmployment());
        existing.setDonations(updated.getDonations());
        existing.setReliefs(updated.getReliefs());
        existing.setSrsDeduction(updated.getSrsDeduction());
        existing.setChargeableIncome(updated.getChargeableIncome());
        existing.setTax(updated.getTax());
        existing.setTaxRebate(updated.getTaxRebate());
        existing.setTaxPayable(updated.getTaxPayable());
        existing.setCountry(updated.getCountry());
        existing.setNotes(updated.getNotes());
        existing.setOwner(updated.getOwner());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting tax record id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
