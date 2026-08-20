package com.myfinance.controller;

import com.myfinance.model.InsurancePolicy;
import com.myfinance.repository.InsurancePolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insurance")
@RequiredArgsConstructor
public class InsuranceController {
    private final InsurancePolicyRepository repository;

    @GetMapping
    public List<InsurancePolicy> getAll(@RequestParam(required = false) Long ownerId) {
        if (ownerId != null) return repository.findByOwnerIdOrderByPolicyNameAsc(ownerId);
        return repository.findByIsActiveTrueOrderByPolicyNameAsc();
    }

    @GetMapping("/{id}")
    public InsurancePolicy getById(@PathVariable Long id) { return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found")); }

    @PostMapping
    public ResponseEntity<InsurancePolicy> create(@RequestBody InsurancePolicy policy) {
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(policy));
    }

    @PutMapping("/{id}")
    public InsurancePolicy update(@PathVariable Long id, @RequestBody InsurancePolicy updated) {
        InsurancePolicy existing = getById(id);
        existing.setPolicyName(updated.getPolicyName());
        existing.setProvider(updated.getProvider());
        existing.setPolicyNumber(updated.getPolicyNumber());
        existing.setPolicyType(updated.getPolicyType());
        existing.setAnnualPremium(updated.getAnnualPremium());
        existing.setCoverageAmount(updated.getCoverageAmount());
        existing.setCashValue(updated.getCashValue());
        existing.setStartDate(updated.getStartDate());
        existing.setMaturityDate(updated.getMaturityDate());
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        existing.setBeneficiary(updated.getBeneficiary());
        existing.setNotes(updated.getNotes());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        InsurancePolicy p = getById(id);
        p.setIsActive(false);
        repository.save(p);
        return ResponseEntity.noContent().build();
    }
}
