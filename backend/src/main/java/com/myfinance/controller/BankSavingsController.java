package com.myfinance.controller;

import com.myfinance.model.BankSavings;
import com.myfinance.repository.BankSavingsRepository;
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
@RequestMapping("/api/bank-savings")
@RequiredArgsConstructor
@Slf4j
public class BankSavingsController {
    private final BankSavingsRepository repository;
    private final TenantContext tenantContext;
    private final com.myfinance.service.CurrencyConversionService fx;

    @GetMapping
    public List<BankSavings> getAll(@RequestParam(required = false) String country,
                                    @RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return repository.findByUserIdAndOwnerIdOrderByAccountNameAsc(uid, ownerId);
        if (country != null) return repository.findByUserIdAndCountry(uid, country);
        return repository.findByUserIdOrderByAccountNameAsc(uid);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Long uid = tenantContext.getCurrentUserId();
        List<BankSavings> all = repository.findByUserIdOrderByAccountNameAsc(uid);
        // Totals are consolidated in the user's base currency: convert each account's
        // original balance via its own currency. Original values remain untouched.
        BigDecimal totalBalance = all.stream()
                .map(s -> fx.toBase(s.getBalance(), currencyCode(s), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal inNetWorth = all.stream()
                .filter(s -> Boolean.TRUE.equals(s.getIncludeInNetWorth()))
                .map(s -> fx.toBase(s.getBalance(), currencyCode(s), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long sgCount = all.stream().filter(s -> "Singapore".equals(s.getCountry())).count();
        long slCount = all.stream().filter(s -> "Sri Lanka".equals(s.getCountry())).count();

        Map<String, Object> result = new HashMap<>();
        result.put("totalAccounts", all.size());
        result.put("totalBalance", totalBalance);
        result.put("inNetWorth", inNetWorth);
        result.put("sgAccounts", sgCount);
        result.put("slAccounts", slCount);
        result.put("baseCurrency", fx.getBaseCurrency(uid));
        return result;
    }

    @PostMapping
    public ResponseEntity<BankSavings> create(@RequestBody BankSavings savings) {
        log.debug("Creating bank savings: accountName={}, bankName={}, balance={}, currency={}, country={}",
                savings.getAccountName(), savings.getBankName(), savings.getBalance(),
                savings.getCurrency(), savings.getCountry());
        // Owner is mandatory for bank savings.
        if (savings.getOwner() == null || savings.getOwner().getId() == null) {
            throw new RuntimeException("Owner is required");
        }
        savings.setUserId(tenantContext.getCurrentUserId());
        savings.setLastUpdated(java.time.LocalDate.now());
        BankSavings saved = repository.save(savings);
        log.info("Created bank savings id={} for userId={}", saved.getId(), saved.getUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public BankSavings update(@PathVariable Long id, @RequestBody BankSavings updated) {
        if (updated.getOwner() == null || updated.getOwner().getId() == null) {
            throw new RuntimeException("Owner is required");
        }
        BankSavings existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setAccountName(updated.getAccountName());
        existing.setBankName(updated.getBankName());
        existing.setAccountNumber(updated.getAccountNumber());
        existing.setBalance(updated.getBalance());
        existing.setCurrency(updated.getCurrency());
        existing.setCountry(updated.getCountry());
        existing.setOwner(updated.getOwner());
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        existing.setLastUpdated(java.time.LocalDate.now());
        existing.setNotes(updated.getNotes());
        return repository.save(existing);
    }

    @PatchMapping("/{id}/balance")
    public BankSavings updateBalance(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        BankSavings existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setBalance(new BigDecimal(body.get("balance").toString()));
        existing.setLastUpdated(java.time.LocalDate.now());
        return repository.save(existing);
    }

    private String currencyCode(BankSavings s) {
        return s.getCurrency() != null ? s.getCurrency().name() : null;
    }

    @PatchMapping("/{id}/net-worth")
    public BankSavings toggleNetWorth(@PathVariable Long id) {
        BankSavings existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setIncludeInNetWorth(!Boolean.TRUE.equals(existing.getIncludeInNetWorth()));
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
