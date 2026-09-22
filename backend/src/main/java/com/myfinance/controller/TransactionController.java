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
    private final com.myfinance.service.IbkrFlexService ibkrFlexService;
    private final com.myfinance.service.IbkrSyncService ibkrSyncService;
    private final com.myfinance.repository.AccountRepository accountRepository;
    private final com.myfinance.repository.OwnerRepository ownerRepository;

    private static final String IBKR_FEATURE = "IBKR_SYNC";

    /** Request for the IBKR trade sync. Token/queryId are used per-request and never stored. */
    public record IbkrSyncRequest(String token, String queryId, Long accountId, Long ownerId,
                                  String mode, LocalDate from, LocalDate to,
                                  java.util.List<String> approvedMismatchTradeIds) {}

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

    /**
     * One-time maintenance: recompute FX-aware realized P/L for the current user's existing SELLs
     * and re-sync their sold positions. Operates only on the caller's own data (tenant-scoped).
     */
    @PostMapping("/recompute-pnl")
    public ResponseEntity<TransactionService.RecomputeResult> recomputePnl() {
        Long uid = tenantContext.getCurrentUserId();
        log.info("Recomputing realized P/L for userId={}", uid);
        return ResponseEntity.ok(transactionService.recomputeRealizedPnlForUser(uid));
    }

    /**
     * Preview an IBKR trade sync: fetch the Flex statement and classify each trade (new / duplicate
     * / mismatch) without writing anything. Gated by the IBKR_SYNC feature. Token never stored/logged.
     */
    @PostMapping("/ibkr-sync/preview")
    public com.myfinance.service.IbkrSyncService.SyncPreview ibkrSyncPreview(@RequestBody IbkrSyncRequest req) {
        var ctx = resolve(req);
        String xml = ibkrFlexService.fetchStatementXml(req.token(), req.queryId());
        var range = dateRange(req);
        return ibkrSyncService.preview(xml, ctx.userId, ctx.account, ctx.owner, range[0], range[1]);
    }

    /**
     * Apply an IBKR trade sync: insert new trades and overwrite only the approved mismatches, then
     * recompute realized P/L. Gated by IBKR_SYNC. Token never stored/logged.
     */
    @PostMapping("/ibkr-sync/apply")
    public com.myfinance.service.IbkrSyncService.SyncResult ibkrSyncApply(@RequestBody IbkrSyncRequest req) {
        var ctx = resolve(req);
        String xml = ibkrFlexService.fetchStatementXml(req.token(), req.queryId());
        var range = dateRange(req);
        java.util.Set<String> approved = req.approvedMismatchTradeIds() == null
                ? java.util.Set.of() : new java.util.HashSet<>(req.approvedMismatchTradeIds());
        try {
            return ibkrSyncService.apply(xml, ctx.userId, ctx.account, ctx.owner, range[0], range[1], approved);
        } catch (RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Preview a trade file import (IBKR Flex XML or "Transaction History" CSV). Classifies each
     * trade (new / duplicate / mismatch) without writing anything. Gated by IBKR_SYNC.
     */
    @PostMapping("/import/preview")
    public com.myfinance.service.IbkrSyncService.SyncPreview importPreview(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam Long accountId, @RequestParam Long ownerId) {
        var ctx = resolveCtx(accountId, ownerId);
        try {
            return ibkrSyncService.previewFile(file.getBytes(), ctx.userId, ctx.account, ctx.owner, null, null);
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
    }

    /**
     * Apply a trade file import: insert new trades and overwrite only the approved mismatches, then
     * recompute realized P/L. Gated by IBKR_SYNC. The file is re-uploaded for this call (stateless).
     */
    @PostMapping("/import/apply")
    public com.myfinance.service.IbkrSyncService.SyncResult importApply(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam Long accountId, @RequestParam Long ownerId,
            @RequestParam(required = false) java.util.List<String> approvedMismatchTradeIds) {
        var ctx = resolveCtx(accountId, ownerId);
        java.util.Set<String> approved = approvedMismatchTradeIds == null
                ? java.util.Set.of() : new java.util.HashSet<>(approvedMismatchTradeIds);
        try {
            return ibkrSyncService.applyFile(file.getBytes(), ctx.userId, ctx.account, ctx.owner, null, null, approved);
        } catch (java.io.IOException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        } catch (RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Resolved, tenant-checked sync context. */
    private record SyncCtx(Long userId, com.myfinance.model.Account account, com.myfinance.model.Owner owner) {}

    private SyncCtx resolve(IbkrSyncRequest req) {
        if (req.token() == null || req.token().isBlank() || req.queryId() == null || req.queryId().isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "IBKR Flex token and Query ID are required");
        }
        return resolveCtx(req.accountId(), req.ownerId());
    }

    /** Feature-gate + tenant-check the account/owner; shared by the live sync and file import. */
    private SyncCtx resolveCtx(Long accountId, Long ownerId) {
        com.myfinance.model.AppUser user = tenantContext.getCurrentUser();
        if (user == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!hasFeature(user, IBKR_FEATURE)) {
            log.warn("User {} attempted an IBKR trade operation without the {} feature", user.getUsername(), IBKR_FEATURE);
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "IBKR sync is not enabled for your account");
        }
        com.myfinance.model.Account account = accountRepository.findById(accountId)
                .filter(a -> user.getId().equals(a.getUserId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account"));
        com.myfinance.model.Owner owner = ownerRepository.findById(ownerId)
                .filter(o -> user.getId().equals(o.getUserId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner"));
        return new SyncCtx(user.getId(), account, owner);
    }

    /** ALL mode → no bounds; RANGE mode → the supplied from/to (either may be null = open-ended). */
    private LocalDate[] dateRange(IbkrSyncRequest req) {
        boolean range = req.mode() != null && req.mode().equalsIgnoreCase("RANGE");
        return range ? new LocalDate[]{req.from(), req.to()} : new LocalDate[]{null, null};
    }

    private boolean hasFeature(com.myfinance.model.AppUser user, String key) {
        String csv = user.getEnabledFeatures();
        if (csv == null || csv.isBlank()) return true;
        for (String f : csv.split(",")) if (key.equals(f.trim())) return true;
        return false;
    }
}
