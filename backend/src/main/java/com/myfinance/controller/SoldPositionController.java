package com.myfinance.controller;

import com.myfinance.model.SoldPosition;
import com.myfinance.security.TenantContext;
import com.myfinance.service.SoldPositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sold-positions")
@RequiredArgsConstructor
@Slf4j
public class SoldPositionController {
    private final SoldPositionService soldPositionService;
    private final TenantContext tenantContext;

    @GetMapping
    public List<SoldPosition> getAll(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long accountId) {
        Long uid = tenantContext.getCurrentUserId();
        if (ownerId != null) return soldPositionService.getByOwner(ownerId);
        if (accountId != null) return soldPositionService.getByAccount(accountId);
        return soldPositionService.getByUser(uid);
    }

    @GetMapping("/short-term")
    public List<SoldPosition> getShortTerm() {
        Long uid = tenantContext.getCurrentUserId();
        return soldPositionService.getShortTermForUser(uid);
    }

    @PostMapping
    public ResponseEntity<SoldPosition> create(@Valid @RequestBody SoldPosition sp) {
        log.info("Creating sold position: quantity={}", sp.getQuantity());
        sp.setUserId(tenantContext.getCurrentUserId());
        SoldPosition saved = soldPositionService.create(sp);
        log.info("Created sold position id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting sold position id={}", id);
        soldPositionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
