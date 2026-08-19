package com.myfinance.controller;

import com.myfinance.model.SoldPosition;
import com.myfinance.service.SoldPositionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sold-positions")
@RequiredArgsConstructor
public class SoldPositionController {
    private final SoldPositionService soldPositionService;

    @GetMapping
    public List<SoldPosition> getAll(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) Long accountId) {
        if (ownerId != null) return soldPositionService.getByOwner(ownerId);
        if (accountId != null) return soldPositionService.getByAccount(accountId);
        return soldPositionService.getAll();
    }

    @GetMapping("/short-term")
    public List<SoldPosition> getShortTerm() { return soldPositionService.getShortTerm(); }

    @PostMapping
    public ResponseEntity<SoldPosition> create(@Valid @RequestBody SoldPosition sp) {
        return ResponseEntity.status(HttpStatus.CREATED).body(soldPositionService.create(sp));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { soldPositionService.delete(id); return ResponseEntity.noContent().build(); }
}
