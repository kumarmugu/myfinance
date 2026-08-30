package com.myfinance.controller;

import com.myfinance.model.PreciousMetal;
import com.myfinance.repository.PreciousMetalRepository;
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
@RequestMapping("/api/precious-metals")
@RequiredArgsConstructor
@Slf4j
public class PreciousMetalController {
    private final PreciousMetalRepository repository;
    private final TenantContext tenantContext;
    private final com.myfinance.service.CurrencyConversionService fx;

    @GetMapping
    public List<PreciousMetal> getAll(@RequestParam(required = false) String metalType) {
        Long uid = tenantContext.getCurrentUserId();
        if (metalType != null) return repository.findByUserIdAndMetalType(uid, metalType.toUpperCase());
        return repository.findByUserIdOrderByPurchaseDateDesc(uid);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Long uid = tenantContext.getCurrentUserId();
        List<PreciousMetal> all = repository.findByUserIdAndStatus(uid, "HELD");

        // Value totals consolidated in the user's base currency (weights stay in grams).
        BigDecimal totalPurchase = all.stream()
                .map(m -> fx.toBase(m.getPurchasePrice(), m.getCurrency(), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCurrent = all.stream()
                .map(m -> fx.toBase(m.getCurrentPrice(), m.getCurrency(), uid))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWeightGold = all.stream()
                .filter(m -> "GOLD".equals(m.getMetalType()))
                .map(PreciousMetal::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalWeightSilver = all.stream()
                .filter(m -> "SILVER".equals(m.getMetalType()))
                .map(PreciousMetal::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("totalItems", all.size());
        result.put("totalPurchaseValue", totalPurchase);
        result.put("totalCurrentValue", totalCurrent);
        result.put("totalGoldGrams", totalWeightGold);
        result.put("totalSilverGrams", totalWeightSilver);
        result.put("gainLoss", totalCurrent.subtract(totalPurchase));
        result.put("baseCurrency", fx.getBaseCurrency(uid));
        return result;
    }

    @PostMapping
    public ResponseEntity<PreciousMetal> create(@RequestBody PreciousMetal metal) {
        log.info("Creating precious metal: type={}, weight={}, form={}", metal.getMetalType(), metal.getWeight(), metal.getForm());
        metal.setUserId(tenantContext.getCurrentUserId());
        PreciousMetal saved = repository.save(metal);
        log.info("Created precious metal id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public PreciousMetal update(@PathVariable Long id, @RequestBody PreciousMetal updated) {
        log.info("Updating precious metal id={}", id);
        PreciousMetal existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Not found"));
        existing.setMetalType(updated.getMetalType());
        existing.setForm(updated.getForm());
        existing.setDescription(updated.getDescription());
        existing.setWeight(updated.getWeight());
        existing.setWeightUnit(updated.getWeightUnit());
        existing.setPurity(updated.getPurity());
        existing.setPurchasePrice(updated.getPurchasePrice());
        existing.setCurrentPrice(updated.getCurrentPrice());
        existing.setCurrency(updated.getCurrency());
        existing.setPurchaseDate(updated.getPurchaseDate());
        existing.setPurchasedFrom(updated.getPurchasedFrom());
        existing.setStorageLocation(updated.getStorageLocation());
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        existing.setStatus(updated.getStatus());
        existing.setSoldPrice(updated.getSoldPrice());
        existing.setSoldDate(updated.getSoldDate());
        existing.setNotes(updated.getNotes());
        existing.setOwner(updated.getOwner());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting precious metal id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
