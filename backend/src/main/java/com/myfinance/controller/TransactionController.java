package com.myfinance.controller;

import com.myfinance.dto.TransactionRequest;
import com.myfinance.model.Transaction;
import com.myfinance.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionController {
    private final TransactionService transactionService;

    @GetMapping
    public List<Transaction> getAll(@RequestParam(required = false) Long ownerId) {
        return ownerId != null ? transactionService.getByOwner(ownerId) : transactionService.getAll();
    }

    @GetMapping("/account/{accountId}")
    public List<Transaction> getByAccount(@PathVariable Long accountId) { return transactionService.getByAccount(accountId); }

    @GetMapping("/asset/{assetId}")
    public List<Transaction> getByAsset(@PathVariable Long assetId) { return transactionService.getByAsset(assetId); }

    @GetMapping("/date-range")
    public List<Transaction> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return transactionService.getByDateRange(start, end);
    }

    @GetMapping("/recent")
    public List<Transaction> getRecent(@RequestParam(defaultValue = "30") int days) { return transactionService.getRecent(days); }

    @PostMapping
    public ResponseEntity<Transaction> create(@Valid @RequestBody TransactionRequest req) {
        Transaction tx = transactionService.create(
                req.getAssetId(), req.getAccountId(), req.getOwnerId(),
                req.getTransactionType(), req.getQuantity(), req.getPricePerUnit(),
                req.getFees(), req.getCurrency(), req.getTransactionDate(), req.getNotes(),
                req.getPurpose());
        return ResponseEntity.status(HttpStatus.CREATED).body(tx);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) { transactionService.delete(id); return ResponseEntity.noContent().build(); }
}
