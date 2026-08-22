package com.myfinance.controller;

import com.myfinance.model.TaxRecord;
import com.myfinance.repository.TaxRecordRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
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
public class TaxController {
    private final TaxRecordRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<TaxRecord> getAll(@RequestParam(required = false) Long ownerId, @RequestParam(required = false) String country) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return repository.findByOwnerIdOrderByAssessmentYearDesc(ownerId);
        if (country != null) return repository.findByCountryOrderByAssessmentYearDesc(country);
        return repository.findByUserIdOrderByAssessmentYearDesc(uid);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        List<TaxRecord> all = repository.findByUserIdOrderByAssessmentYearDesc(tenantContext.getCurrentUserId());
        BigDecimal totalPaid = all.stream()
                .map(t -> t.getTaxPayable() != null ? t.getTaxPayable() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalIncome = all.stream()
                .map(TaxRecord::getEmployment)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("totalTaxPaid", totalPaid);
        result.put("totalIncome", totalIncome);
        result.put("years", all.size());
        return result;
    }

    @GetMapping("/{id}")
    public TaxRecord getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Tax record not found"));
    }

    @PostMapping
    public ResponseEntity<TaxRecord> create(@RequestBody TaxRecord record) {
        record.setUserId(tenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(record));
    }

    @PutMapping("/{id}")
    public TaxRecord update(@PathVariable Long id, @RequestBody TaxRecord updated) {
        TaxRecord existing = getById(id);
        existing.setAssessmentYear(updated.getAssessmentYear());
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
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
