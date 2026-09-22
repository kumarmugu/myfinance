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
    private final com.myfinance.service.TigerSyncService tigerSyncService;
    private final com.myfinance.repository.AccountRepository accountRepository;
    private final com.myfinance.repository.OwnerRepository ownerRepository;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
    private final com.myfinance.service.BrokerCredentialService brokerCredentialService;

    /**
     * Request for a live broker trade sync. Credentials come from the account's stored config, not
     * here. {@code broker} selects the integration (IBKR / TIGER); null defaults to IBKR for
     * backward compatibility with older clients.
     */
    public record IbkrSyncRequest(Long accountId, Long ownerId, String broker,
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
     * Preview a scoped bulk delete: how many transactions, holdings and sold positions would be
     * removed for this owner+account. Both owner and account are required (never a delete-all).
     */
    @GetMapping("/bulk-count")
    public TransactionService.BulkDeleteResult bulkCount(@RequestParam Long ownerId, @RequestParam Long accountId) {
        var ctx = resolveBulk(ownerId, accountId);
        return transactionService.countForOwnerAccount(ctx.userId, ownerId, accountId);
    }

    /**
     * Bulk-delete this user's transactions for one owner+account (also removing the derived holdings
     * and sold positions, then recomputing P/L). Requires the account password.
     */
    @PostMapping("/bulk-delete")
    public TransactionService.BulkDeleteResult bulkDelete(@RequestBody java.util.Map<String, Object> body) {
        Long ownerId = asLong(body.get("ownerId"));
        Long accountId = asLong(body.get("accountId"));
        var ctx = resolveBulk(ownerId, accountId);
        verifyPassword(ctx.user, (String) body.get("password"));
        var result = transactionService.deleteForOwnerAccount(ctx.userId, ownerId, accountId);
        log.info("Bulk-deleted transactions for user {} owner {} account {}: {}", ctx.user.getUsername(), ownerId, accountId, result);
        return result;
    }

    private record BulkCtx(com.myfinance.model.AppUser user, Long userId) {}

    /** Feature-agnostic bulk-scope guard: both owner+account required and owned by the caller. */
    private BulkCtx resolveBulk(Long ownerId, Long accountId) {
        com.myfinance.model.AppUser user = tenantContext.getCurrentUser();
        if (user == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (ownerId == null || accountId == null) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Both owner and account are required");
        }
        accountRepository.findById(accountId).filter(a -> user.getId().equals(a.getUserId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account"));
        ownerRepository.findById(ownerId).filter(o -> user.getId().equals(o.getUserId()))
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner"));
        return new BulkCtx(user, user.getId());
    }

    private void verifyPassword(com.myfinance.model.AppUser user, String password) {
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect password");
        }
    }

    private Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Preview an IBKR trade sync: fetch the Flex statement and classify each trade (new / duplicate
     * / mismatch) without writing anything. Gated by the IBKR_SYNC feature. Token never stored/logged.
     */
    @PostMapping("/ibkr-sync/preview")
    public com.myfinance.service.IbkrSyncService.SyncPreview ibkrSyncPreview(@RequestBody IbkrSyncRequest req) {
        var ctx = resolveCtx(req.accountId(), req.ownerId());
        var range = dateRange(req);
        try {
            if (brokerOf(req) == com.myfinance.model.enums.Broker.TIGER) {
                return tigerSyncService.preview(ctx.userId, ctx.account, ctx.owner, range[0], range[1]);
            }
            String xml = fetchIbkrStatement(ctx.userId, ctx.account.getId());
            return ibkrSyncService.preview(xml, ctx.userId, ctx.account, ctx.owner, range[0], range[1]);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /**
     * Apply a live broker trade sync: insert new trades and overwrite only the approved mismatches,
     * then recompute realized P/L. Gated by BROKER_SYNC. Uses the account's stored credential.
     */
    @PostMapping("/ibkr-sync/apply")
    public com.myfinance.service.IbkrSyncService.SyncResult ibkrSyncApply(@RequestBody IbkrSyncRequest req) {
        var ctx = resolveCtx(req.accountId(), req.ownerId());
        var range = dateRange(req);
        java.util.Set<String> approved = req.approvedMismatchTradeIds() == null
                ? java.util.Set.of() : new java.util.HashSet<>(req.approvedMismatchTradeIds());
        try {
            if (brokerOf(req) == com.myfinance.model.enums.Broker.TIGER) {
                return tigerSyncService.apply(ctx.userId, ctx.account, ctx.owner, range[0], range[1], approved);
            }
            String xml = fetchIbkrStatement(ctx.userId, ctx.account.getId());
            return ibkrSyncService.apply(xml, ctx.userId, ctx.account, ctx.owner, range[0], range[1], approved);
        } catch (org.springframework.web.server.ResponseStatusException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    /** Resolve the requested broker; unknown/blank defaults to IBKR (back-compat with older clients). */
    private com.myfinance.model.enums.Broker brokerOf(IbkrSyncRequest req) {
        if (req.broker() == null || req.broker().isBlank()) return com.myfinance.model.enums.Broker.IBKR;
        try {
            return com.myfinance.model.enums.Broker.valueOf(req.broker().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return com.myfinance.model.enums.Broker.IBKR;
        }
    }

    /**
     * Load the account's stored IBKR Flex credential (Query ID + token), decrypt it and fetch the
     * statement. The token is used only for this call and is never logged. Errors if not configured.
     */
    private String fetchIbkrStatement(Long userId, Long accountId) {
        var cred = brokerCredentialService.decryptFor(userId, accountId, com.myfinance.model.enums.Broker.IBKR)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "No IBKR credentials saved for this account — add them on the Account page"));
        String queryId = cred.meta1();
        String token = cred.secret1();
        if (token == null || token.isBlank() || queryId == null || queryId.isBlank()) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "IBKR credentials are incomplete — set both the Flex token and Query ID on the Account page");
        }
        return ibkrFlexService.fetchStatementXml(token, queryId);
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

    /** Feature-gate + tenant-check the account/owner; shared by the live sync and file import. */
    private SyncCtx resolveCtx(Long accountId, Long ownerId) {
        com.myfinance.model.AppUser user = tenantContext.getCurrentUser();
        if (user == null) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!com.myfinance.security.FeatureFlags.hasBrokerSync(user)) {
            log.warn("User {} attempted a broker trade operation without the broker-sync feature", user.getUsername());
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Broker sync is not enabled for your account");
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


}
