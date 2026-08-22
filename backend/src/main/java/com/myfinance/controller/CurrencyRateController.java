package com.myfinance.controller;

import com.myfinance.model.CurrencyRate;
import com.myfinance.model.enums.Currency;
import com.myfinance.repository.CurrencyRateRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/currency-rates")
@RequiredArgsConstructor
public class CurrencyRateController {
    private final CurrencyRateRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<CurrencyRate> getAll() { return repository.findByUserId(tenantContext.getCurrentUserId()); }

    @GetMapping("/currencies")
    public List<String> getAvailableCurrencies() {
        Set<String> currencies = new TreeSet<>();
        // Add all enum values
        for (Currency c : Currency.values()) {
            currencies.add(c.name());
        }
        // Add any additional from stored rates
        List<CurrencyRate> rates = repository.findByUserId(tenantContext.getCurrentUserId());
        for (CurrencyRate r : rates) {
            currencies.add(r.getFromCurrency());
            currencies.add(r.getToCurrency());
        }
        return new ArrayList<>(currencies);
    }

    @PostMapping
    public ResponseEntity<CurrencyRate> create(@RequestBody CurrencyRate rate) {
        rate.setUserId(tenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(repository.save(rate));
    }

    @PutMapping("/{id}")
    public CurrencyRate update(@PathVariable Long id, @RequestBody CurrencyRate updated) {
        CurrencyRate existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Rate not found"));
        existing.setRate(updated.getRate());
        existing.setEffectiveDate(updated.getEffectiveDate());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { repository.deleteById(id); return ResponseEntity.noContent().build(); }
}
