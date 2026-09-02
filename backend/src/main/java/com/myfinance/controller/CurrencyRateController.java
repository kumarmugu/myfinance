package com.myfinance.controller;

import com.myfinance.model.CurrencyRate;
import com.myfinance.model.UserCurrency;
import com.myfinance.repository.CurrencyRateRepository;
import com.myfinance.repository.UserCurrencyRepository;
import com.myfinance.security.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/currency-rates")
@RequiredArgsConstructor
@Slf4j
public class CurrencyRateController {
    private final CurrencyRateRepository repository;
    private final UserCurrencyRepository userCurrencyRepository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<CurrencyRate> getAll() { return repository.findByUserId(tenantContext.getCurrentUserId()); }

    // ─── User Currencies (CRUD) ───

    @GetMapping("/currencies")
    public List<String> getAvailableCurrencies() {
        Long uid = tenantContext.getCurrentUserId();
        List<UserCurrency> userCurrencies = userCurrencyRepository.findByUserIdOrderByCodeAsc(uid);
        return userCurrencies.stream().map(UserCurrency::getCode).collect(Collectors.toList());
    }

    @GetMapping("/currencies/all")
    public List<UserCurrency> getAllCurrencies() {
        Long uid = tenantContext.getCurrentUserId();
        return userCurrencyRepository.findByUserIdOrderByCodeAsc(uid);
    }

    @PostMapping("/currencies")
    public ResponseEntity<?> addCurrency(@RequestBody Map<String, String> body) {
        Long uid = tenantContext.getCurrentUserId();
        String code = body.get("code") != null ? body.get("code").toUpperCase().trim() : "";
        String name = body.getOrDefault("name", "");

        if (code.isEmpty() || code.length() > 5) {
            return ResponseEntity.badRequest().body(Map.of("error", "Currency code must be 1-5 characters"));
        }
        if (userCurrencyRepository.existsByUserIdAndCode(uid, code)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Currency '" + code + "' already exists"));
        }

        UserCurrency saved = userCurrencyRepository.save(UserCurrency.builder()
                .userId(uid).code(code).name(name).build());
        log.info("Added currency code={} for userId={}", code, uid);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/currencies/{code}")
    public ResponseEntity<?> removeCurrency(@PathVariable String code) {
        Long uid = tenantContext.getCurrentUserId();
        Optional<UserCurrency> found = userCurrencyRepository.findByUserIdAndCode(uid, code.toUpperCase());
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        // Check if any FX rates use this currency
        List<CurrencyRate> rates = repository.findByUserId(uid);
        boolean inUse = rates.stream().anyMatch(r ->
                r.getFromCurrency().equals(code.toUpperCase()) || r.getToCurrency().equals(code.toUpperCase()));
        if (inUse) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Currency '" + code + "' is used in FX rates. Delete those rates first."));
        }
        userCurrencyRepository.delete(found.get());
        log.info("Removed currency code={} for userId={}", code, uid);
        return ResponseEntity.noContent().build();
    }

    // ─── FX Rates (CRUD) ───

    /**
     * Upsert a rate for a currency pair. We keep exactly ONE row per (user, from, to) — if a
     * rate for the pair already exists it is updated in place rather than adding a history row.
     * effectiveDate is set automatically to today; the client no longer supplies it.
     */
    @PostMapping
    public ResponseEntity<CurrencyRate> create(@RequestBody CurrencyRate rate) {
        Long uid = tenantContext.getCurrentUserId();
        String from = norm(rate.getFromCurrency());
        String to = norm(rate.getToCurrency());
        log.info("Upserting currency rate: {}→{}, rate={}, spreadPct={}", from, to, rate.getRate(), rate.getSpreadPct());

        CurrencyRate entity = repository.findByUserIdAndFromCurrencyAndToCurrency(uid, from, to)
                .orElseGet(CurrencyRate::new);
        entity.setUserId(uid);
        entity.setFromCurrency(from);
        entity.setToCurrency(to);
        entity.setRate(rate.getRate());
        entity.setSpreadPct(rate.getSpreadPct());
        entity.setEffectiveDate(LocalDate.now());
        CurrencyRate saved = repository.save(entity);
        log.info("Saved currency rate id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public CurrencyRate update(@PathVariable Long id, @RequestBody CurrencyRate updated) {
        log.info("Updating currency rate id={}", id);
        CurrencyRate existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Rate not found"));
        existing.setRate(updated.getRate());
        existing.setSpreadPct(updated.getSpreadPct());
        existing.setEffectiveDate(LocalDate.now());
        return repository.save(existing);
    }

    private String norm(String s) { return s == null ? "" : s.trim().toUpperCase(); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting currency rate id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
