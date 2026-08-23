package com.myfinance.controller;

import com.myfinance.model.Dividend;
import com.myfinance.security.TenantContext;
import com.myfinance.service.DividendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dividends")
@RequiredArgsConstructor
@Slf4j
public class DividendController {
    private final DividendService dividendService;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Dividend> getAll(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Integer year) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return dividendService.getByOwner(ownerId);
        if (accountId != null) return dividendService.getByAccount(accountId);
        if (year != null) return dividendService.getByYear(year);
        return dividendService.getByUser(uid);
    }

    @GetMapping("/summary")
    public List<Object[]> getSummaryByYear() {
        Long uid = tenantContext.getCurrentUserId();
        return dividendService.getSummaryByYearForUser(uid);
    }

    @PostMapping
    public ResponseEntity<Dividend> create(@Valid @RequestBody Dividend dividend) {
        log.info("Creating dividend: amount={}", dividend.getAmount());
        dividend.setUserId(tenantContext.getCurrentUserId());
        Dividend saved = dividendService.create(dividend);
        log.info("Created dividend id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting dividend id={}", id);
        dividendService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
