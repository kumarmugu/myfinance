package com.myfinance.controller;

import com.myfinance.model.Owner;
import com.myfinance.security.TenantContext;
import com.myfinance.service.OwnerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
@Slf4j
public class OwnerController {
    private final OwnerService ownerService;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Owner> getAll() { return ownerService.getByUserId(tenantContext.getCurrentUserId()); }

    @GetMapping("/{id}")
    public Owner getById(@PathVariable Long id) { return ownerService.getById(id); }

    @PostMapping
    public ResponseEntity<Owner> create(@RequestBody Owner owner) {
        log.info("Creating owner: name={}", owner.getName());
        owner.setUserId(tenantContext.getCurrentUserId());
        Owner saved = ownerService.create(owner);
        log.info("Created owner id={}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    public Owner update(@PathVariable Long id, @RequestBody Owner owner) {
        log.info("Updating owner id={}", id);
        return ownerService.update(id, owner);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting owner id={}", id);
        ownerService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
