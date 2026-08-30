package com.myfinance.controller;

import com.myfinance.model.Bond;
import com.myfinance.repository.BondRepository;
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
@RequestMapping("/api/bonds")
@RequiredArgsConstructor
@Slf4j
public class BondController {
    private final BondRepository repository;
    private final TenantContext tenantContext;
    private final com.myfinance.service.CurrencyConversionService fx;

    @GetMapping
    public List<Bond> getAll(@RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return repository.findByOwnerIdOrderByMaturityDateAsc(ownerId);
        return repository.findByUserIdOrderByMaturityDateAsc(uid);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Long uid = tenantContext.getCurrentUserId();
        List<Bond> held = repository.findByUserIdAndStatus(uid, "HELD");

        // Totals consolidated in the user's base currency, converting each bond's
        // original-currency values. Original per-bond values are never modified.
        BigDecimal totalFace = held.stream()
                .map(b -> fx.toBase(b.getFaceValue(), b.getCurrency(), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalInvested = held.stream()
                .map(b -> fx.toBase(b.getPurchasePrice(), b.getCurrency(), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCurrent = held.stream()
                .map(b -> fx.toBase(b.getCurrentValue(), b.getCurrency(), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("totalBonds", held.size());
        result.put("totalFaceValue", totalFace);
        result.put("totalInvested", totalInvested);
        result.put("totalCurrentValue", totalCurrent);
        result.put("gainLoss", totalCurrent.subtract(totalInvested));
        result.put("baseCurrency", fx.getBaseCurrency(uid));
        return result;
    }

    @GetMapping("/{id}")
    public Bond getById(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new RuntimeException("Bond not found"));
    }

    @PostMapping
    public ResponseEntity<Bond> create(@RequestBody Bond bond) {
        log.info("Creating bond: name={}, issuer={}", bond.getName(), bond.getIssuer());
        bond.setUserId(tenantContext.getCurrentUserId());
        Bond saved = repository.save(bond);
        log.info("Created bond id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public Bond update(@PathVariable Long id, @RequestBody Bond updated) {
        log.info("Updating bond id={}", id);
        Bond existing = getById(id);
        existing.setName(updated.getName());
        existing.setIssuer(updated.getIssuer());
        existing.setBondType(updated.getBondType());
        existing.setIsin(updated.getIsin());
        existing.setCurrency(updated.getCurrency());
        existing.setFaceValue(updated.getFaceValue());
        existing.setPurchasePrice(updated.getPurchasePrice());
        existing.setCurrentValue(updated.getCurrentValue());
        existing.setCouponRate(updated.getCouponRate());
        existing.setCouponFrequency(updated.getCouponFrequency());
        existing.setPurchaseDate(updated.getPurchaseDate());
        existing.setMaturityDate(updated.getMaturityDate());
        existing.setStatus(updated.getStatus());
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        existing.setNotes(updated.getNotes());
        existing.setOwner(updated.getOwner());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting bond id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
