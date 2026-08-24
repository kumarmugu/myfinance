package com.myfinance.controller;

import com.myfinance.model.Property;
import com.myfinance.repository.PropertyRepository;
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
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Slf4j
public class PropertyController {
    private final PropertyRepository repository;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Property> getAll() {
        Long uid = tenantContext.getCurrentUserId();
        return repository.findByUserIdOrderByPropertyNameAsc(uid);
    }

    @GetMapping("/summary")
    public Map<String, Object> getSummary() {
        Long uid = tenantContext.getCurrentUserId();
        List<Property> all = repository.findByUserIdOrderByPropertyNameAsc(uid);
        List<Property> owned = all.stream().filter(p -> "OWNED".equals(p.getStatus())).toList();

        BigDecimal totalValue = owned.stream()
                .map(p -> p.getCurrentValue() != null ? p.getCurrentValue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalLoan = owned.stream()
                .map(p -> p.getOutstandingLoan() != null ? p.getOutstandingLoan() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalEquity = totalValue.subtract(totalLoan);
        BigDecimal monthlyRental = all.stream()
                .filter(p -> p.getMonthlyRental() != null)
                .map(Property::getMonthlyRental)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("totalProperties", owned.size());
        result.put("totalValue", totalValue);
        result.put("totalLoan", totalLoan);
        result.put("totalEquity", totalEquity);
        result.put("monthlyRental", monthlyRental);
        return result;
    }

    @PostMapping
    public ResponseEntity<Property> create(@RequestBody Property property) {
        log.info("Creating property: name={}, type={}", property.getPropertyName(), property.getPropertyType());
        property.setUserId(tenantContext.getCurrentUserId());
        Property saved = repository.save(property);
        log.info("Created property id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public Property update(@PathVariable Long id, @RequestBody Property updated) {
        log.info("Updating property id={}", id);
        Property existing = repository.findById(id).orElseThrow(() -> new RuntimeException("Property not found"));
        existing.setPropertyName(updated.getPropertyName());
        existing.setPropertyType(updated.getPropertyType());
        existing.setAddress(updated.getAddress());
        existing.setCountry(updated.getCountry());
        existing.setPurchasePrice(updated.getPurchasePrice());
        existing.setCurrentValue(updated.getCurrentValue());
        existing.setOutstandingLoan(updated.getOutstandingLoan());
        existing.setCurrency(updated.getCurrency());
        existing.setPurchaseDate(updated.getPurchaseDate());
        existing.setTenure(updated.getTenure());
        existing.setAreaSize(updated.getAreaSize());
        existing.setAreaUnit(updated.getAreaUnit());
        existing.setOwnership(updated.getOwnership());
        existing.setIncludeInNetWorth(updated.getIncludeInNetWorth());
        existing.setStatus(updated.getStatus());
        existing.setMonthlyRental(updated.getMonthlyRental());
        existing.setNotes(updated.getNotes());
        existing.setOwner(updated.getOwner());
        return repository.save(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting property id={}", id);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}
