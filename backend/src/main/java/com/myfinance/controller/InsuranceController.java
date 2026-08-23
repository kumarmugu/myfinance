package com.myfinance.controller;

import com.myfinance.model.InsuranceBonusEntry;
import com.myfinance.model.InsurancePolicy;
import com.myfinance.repository.InsuranceBonusEntryRepository;
import com.myfinance.repository.InsurancePolicyRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/insurance")
@RequiredArgsConstructor
@Slf4j
public class InsuranceController {
    private final InsurancePolicyRepository repository;
    private final InsuranceBonusEntryRepository bonusRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<InsurancePolicy> getAll(@RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return repository.findByOwnerIdOrderByPolicyNameAsc(ownerId);
        return repository.findByUserIdAndIsActiveTrue(uid);
    }

    @GetMapping("/{id}")
    public InsurancePolicy getById(@PathVariable Long id) { return repository.findById(id).orElseThrow(() -> new RuntimeException("Not found")); }

    @PostMapping
    public ResponseEntity<InsurancePolicy> create(@RequestBody InsurancePolicy policy) {
        log.info("Creating insurance policy: name={}, type={}", policy.getPolicyName(), policy.getPolicyType());
        policy.setUserId(tenantContext.getCurrentUserId());
        InsurancePolicy saved = repository.save(policy);
        log.info("Created insurance policy id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public InsurancePolicy update(@PathVariable Long id, @RequestBody InsurancePolicy updated) {
        log.info("Updating insurance policy id={}", id);
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
        log.info("Soft-deleting insurance policy id={}", id);
        InsurancePolicy p = getById(id);
        p.setIsActive(false);
        repository.save(p);
        return ResponseEntity.noContent().build();
    }

    // ─── Bonus Schedule Entries ───
    @GetMapping("/{policyId}/bonus")
    public List<InsuranceBonusEntry> getBonusEntries(@PathVariable Long policyId) {
        return bonusRepository.findByPolicyIdOrderByYearNumberAsc(policyId);
    }

    @PostMapping("/{policyId}/bonus")
    public ResponseEntity<InsuranceBonusEntry> createBonusEntry(@PathVariable Long policyId, @RequestBody InsuranceBonusEntry entry) {
        log.info("Creating bonus entry for policyId={}, year={}", policyId, entry.getYearNumber());
        InsurancePolicy policy = getById(policyId);
        entry.setPolicy(policy);
        entry.setUserId(tenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(bonusRepository.save(entry));
    }

    @PutMapping("/bonus/{entryId}")
    public InsuranceBonusEntry updateBonusEntry(@PathVariable Long entryId, @RequestBody InsuranceBonusEntry updated) {
        log.info("Updating bonus entry id={}", entryId);
        InsuranceBonusEntry existing = bonusRepository.findById(entryId).orElseThrow(() -> new RuntimeException("Entry not found"));
        existing.setYearNumber(updated.getYearNumber());
        existing.setYearDate(updated.getYearDate());
        existing.setAge(updated.getAge());
        existing.setPremiumAmount(updated.getPremiumAmount());
        existing.setExpectedBonus(updated.getExpectedBonus());
        existing.setExpectedBonusTotal(updated.getExpectedBonusTotal());
        existing.setExpectedTotal(updated.getExpectedTotal());
        existing.setActualBonus(updated.getActualBonus());
        existing.setActualBonusTotal(updated.getActualBonusTotal());
        existing.setNotes(updated.getNotes());
        return bonusRepository.save(existing);
    }

    @DeleteMapping("/bonus/{entryId}")
    public ResponseEntity<Void> deleteBonusEntry(@PathVariable Long entryId) {
        log.info("Deleting bonus entry id={}", entryId);
        bonusRepository.deleteById(entryId);
        return ResponseEntity.noContent().build();
    }

    @Transactional
    @PostMapping("/{policyId}/bonus/batch")
    public ResponseEntity<List<InsuranceBonusEntry>> batchCreateBonusEntries(@PathVariable Long policyId, @RequestBody List<InsuranceBonusEntry> entries) {
        InsurancePolicy policy = getById(policyId);
        entries.forEach(e -> e.setPolicy(policy));
        return ResponseEntity.status(HttpStatus.CREATED).body(bonusRepository.saveAll(entries));
    }
}
