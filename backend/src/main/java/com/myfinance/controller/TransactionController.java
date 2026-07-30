package com.myfinance.controller;

import com.myfinance.dto.TransactionRequest;
import com.myfinance.model.Transaction;
import com.myfinance.model.enums.AssetType;
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
    public List<Transaction> getAllTransactions() {
        return transactionService.getAllTransactions();
    }

    @GetMapping("/{id}")
    public Transaction getTransactionById(@PathVariable Long id) {
        return transactionService.getTransactionById(id);
    }

    @GetMapping("/asset/{assetId}")
    public List<Transaction> getByAsset(@PathVariable Long assetId) {
        return transactionService.getTransactionsByAsset(assetId);
    }

    @GetMapping("/account/{accountId}")
    public List<Transaction> getByAccount(@PathVariable Long accountId) {
        return transactionService.getTransactionsByAccount(accountId);
    }

    @GetMapping("/type/{assetType}")
    public List<Transaction> getByAssetType(@PathVariable AssetType assetType) {
        return transactionService.getTransactionsByAssetType(assetType);
    }

    @GetMapping("/date-range")
    public List<Transaction> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return transactionService.getTransactionsByDateRange(start, end);
    }

    @GetMapping("/recent")
    public List<Transaction> getRecentTransactions(@RequestParam(defaultValue = "30") int days) {
        return transactionService.getRecentTransactions(days);
    }

    @PostMapping
    public ResponseEntity<Transaction> createTransaction(@Valid @RequestBody TransactionRequest request) {
        Transaction transaction = transactionService.createTransaction(
                request.getAssetId(),
                request.getAccountId(),
                request.getTransactionType(),
                request.getQuantity(),
                request.getPricePerUnit(),
                request.getFees(),
                request.getTransactionDate(),
                request.getNotes()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(transaction);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTransaction(@PathVariable Long id) {
        transactionService.deleteTransaction(id);
        return ResponseEntity.noContent().build();
    }
}
