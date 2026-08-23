package com.myfinance.controller;

import com.myfinance.model.GenericFixedDeposit;
import com.myfinance.repository.GenericFixedDepositRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/generic-fd")
@RequiredArgsConstructor
@Slf4j
public class GenericFixedDepositController {
    private final GenericFixedDepositRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<GenericFixedDeposit> getAll() {
        Long uid = tenantContext.getCurrentUserId();
        return repository.findByUserIdOrderByMaturityDateAsc(uid);
    }

    @PostMapping
    public ResponseEntity<GenericFixedDeposit> create(@RequestBody GenericFixedDeposit fd) {
        log.info("Creating generic FD: bank={}, principal={}", fd.getBankName(), fd.getPrincipalAmount());
        fd.setUserId(tenantContext.getCurrentUserId());
        if (fd.getExpectedInterest() == null && fd.getPrincipalAmount() != null && fd.getInterestRate() != null) {
            long days = ChronoUnit.DAYS.between(fd.getStartDate(), fd.getMaturityDate());
            fd.setExpectedInterest(fd.getPrincipalAmount()
                    .multiply(fd.getInterestRate())
                    .multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(36500), 2, RoundingMode.HALF_UP));
        }
        GenericFixedDeposit saved = repository.save(fd);
        log.info("Created generic FD id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public GenericFixedDeposit update(@PathVariable Long id, @RequestBody GenericFixedDeposit updated) {
        log.info("Updating generic FD id={}", id);
        GenericFixedDeposit existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setBankName(updated.getBankName());
        existing.setAccountNumber(updated.getAccountNumber());
        existing.setPrincipalAmount(updated.getPrincipalAmount());
        existing.setInterestRate(updated.getInterestRate());
        existing.setStartDate(updated.getStartDate());
        existing.setMaturityDate(updated.getMaturityDate());
        existing.setTenure(updated.getTenure());
        existing.setCurrency(updated.getCurrency());
        existing.setStatus(updated.getStatus());
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        existing.setNotes(updated.getNotes());
        // Recalculate interest
        if (existing.getPrincipalAmount() != null && existing.getInterestRate() != null) {
            long days = ChronoUnit.DAYS.between(existing.getStartDate(), existing.getMaturityDate());
            existing.setExpectedInterest(existing.getPrincipalAmount()
                    .multiply(existing.getInterestRate())
                    .multiply(BigDecimal.valueOf(days))
                    .divide(BigDecimal.valueOf(36500), 2, RoundingMode.HALF_UP));
        }
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting generic FD id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
