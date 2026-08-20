package com.myfinance.controller;

import com.myfinance.model.CurrencyRate;
import com.myfinance.repository.CurrencyRateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/currency-rates")
@RequiredArgsConstructor
public class CurrencyRateController {
    private final CurrencyRateRepository repository;

    @GetMapping
    public List<CurrencyRate> getAll() { return repository.findAll(); }

    @PostMapping
    public ResponseEntity<CurrencyRate> create(@RequestBody CurrencyRate rate) {
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
