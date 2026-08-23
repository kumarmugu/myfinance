package com.myfinance.controller;

import com.myfinance.model.CurrencyRate;
import com.myfinance.model.enums.Currency;
import com.myfinance.repository.CurrencyRateRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/currency-rates")
@RequiredArgsConstructor
@Slf4j
public class CurrencyRateController {
    private final CurrencyRateRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<CurrencyRate> getAll() { return repository.findByUserId(tenantContext.getCurrentUserId()); }

    @GetMapping("/currencies")
    public List<String> getAvailableCurrencies() {
        Set<String> currencies = new TreeSet<>();
        // Only return currencies from the user's stored rates
        List<CurrencyRate> rates = repository.findByUserId(tenantContext.getCurrentUserId());
        for (CurrencyRate r : rates) {
            currencies.add(r.getFromCurrency());
            currencies.add(r.getToCurrency());
        }
        // Always include SGD and USD as base currencies
        currencies.add("SGD");
        currencies.add("USD");
        return new ArrayList<>(currencies);
    }

    @PostMapping
    public ResponseEntity<CurrencyRate> create(@RequestBody CurrencyRate rate) {
        log.info("Creating currency rate: {}→{}, rate={}", rate.getFromCurrency(), rate.getToCurrency(), rate.getRate());
        rate.setUserId(tenantContext.getCurrentUserId());
        CurrencyRate saved = repository.save(rate);
        log.info("Created currency rate id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public CurrencyRate update(@PathVariable Long id, @RequestBody CurrencyRate updated) {
        log.info("Updating currency rate id={}", id);
        CurrencyRate existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Rate not found"));
        existing.setRate(updated.getRate());
        existing.setEffectiveDate(updated.getEffectiveDate());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting currency rate id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
