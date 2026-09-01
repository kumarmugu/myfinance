package com.myfinance.controller;

import com.myfinance.dto.TransactionRequest;
import com.myfinance.model.Transaction;
import com.myfinance.security.TenantContext;
import com.myfinance.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {
    private final TransactionService transactionService;
    private final TenantContext tenantContext;

    @GetMapping
    public List<Transaction> getAll(@RequestParam(required = false) Long ownerId) {
        Long uid = tenantContext.getCurrentUserId();
        return ownerId != null ? transactionService.getByOwner(ownerId) : transactionService.getByUser(uid);
    }

    @GetMapping("/account/{accountId}")
    public List<Transaction> getByAccount(@PathVariable Long accountId) {
        Long uid = tenantContext.getCurrentUserId();
        return transactionService.getByAccountForUser(uid, accountId);
    }

    @GetMapping("/asset/{assetId}")
    public List<Transaction> getByAsset(@PathVariable Long assetId) {
        Long uid = tenantContext.getCurrentUserId();
        return transactionService.getByAssetForUser(uid, assetId);
    }

    @GetMapping("/date-range")
    public List<Transaction> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        Long uid = tenantContext.getCurrentUserId();
        return transactionService.getByDateRangeForUser(uid, start, end);
    }

    @GetMapping("/recent")
    public List<Transaction> getRecent(@RequestParam(defaultValue = "30") int days) {
        Long uid = tenantContext.getCurrentUserId();
        return transactionService.getRecentForUser(uid, days);
    }

    @PostMapping
    public ResponseEntity<Transaction> create(@Valid @RequestBody TransactionRequest req) {
        log.info("Creating transaction: assetId={}, type={}, quantity={}, price={}", req.getAssetId(), req.getTransactionType(), req.getQuantity(), req.getPricePerUnit());
        Transaction tx = transactionService.create(
                req.getAssetId(), req.getAccountId(), req.getOwnerId(),
                req.getTransactionType(), req.getQuantity(), req.getPricePerUnit(),
                req.getFees(), req.getCurrency(), req.getTransactionDate(), req.getNotes(),
                req.getPurpose(), req.getFeeCurrency(), req.getFxRateToBase());
        log.info("Created transaction id={}", tx.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Transaction> update(@PathVariable Long id, @Valid @RequestBody TransactionRequest req) {
        Long uid = tenantContext.getCurrentUserId();
        // Tenant isolation: only the owner of the record may modify it.
        Transaction existing = transactionService.getByUser(uid).stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
        if (existing == null) {
            return ResponseEntity.notFound().build();
        }
        log.info("Updating transaction id={}: assetId={}, type={}, quantity={}", id, req.getAssetId(), req.getTransactionType(), req.getQuantity());
        Transaction tx = transactionService.update(
                id, req.getAssetId(), req.getAccountId(), req.getOwnerId(),
                req.getTransactionType(), req.getQuantity(), req.getPricePerUnit(),
                req.getFees(), req.getCurrency(), req.getTransactionDate(), req.getNotes(),
                req.getPurpose(), req.getFeeCurrency(), req.getFxRateToBase());
        return ResponseEntity.ok(tx);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        log.info("Deleting transaction id={}", id);
        transactionService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
