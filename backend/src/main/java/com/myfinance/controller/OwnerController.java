package com.myfinance.controller;

import com.myfinance.model.Owner;
import com.myfinance.security.TenantContext;
import com.myfinance.service.OwnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/owners")
@RequiredArgsConstructor
public class OwnerController {
    private final OwnerService ownerService;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Owner> getAll() { return ownerService.getByUserId(tenantContext.getCurrentUserId()); }

    @GetMapping("/{id}")
    public Owner getById(@PathVariable Long id) { return ownerService.getById(id); }

    @PostMapping
    public ResponseEntity<Owner> create(@RequestBody Owner owner) {
        owner.setUserId(tenantContext.getCurrentUserId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ownerService.create(owner));
    }

    @PutMapping("/{id}")
    public Owner update(@PathVariable Long id, @RequestBody Owner owner) { return ownerService.update(id, owner); }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { ownerService.delete(id); return ResponseEntity.noContent().build(); }
}
