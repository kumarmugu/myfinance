package com.myfinance.controller;

import com.myfinance.model.AppUser;
import com.myfinance.model.Account;
import com.myfinance.model.Dividend;
import com.myfinance.model.Owner;
import com.myfinance.repository.AccountRepository;
import com.myfinance.repository.OwnerRepository;
import com.myfinance.security.TenantContext;
import com.myfinance.service.DividendImportService;
import com.myfinance.service.DividendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/dividends")
@RequiredArgsConstructor
@Slf4j
public class DividendController {
    private final DividendService dividendService;
    private final DividendImportService dividendImportService;
    private final com.myfinance.service.IbkrFlexService ibkrFlexService;
    private final com.myfinance.service.BrokerCredentialService brokerCredentialService;
    private final AccountRepository accountRepository;
    private final OwnerRepository ownerRepository;
    private final TenantContext tenantContext;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    /** Per-user feature key that unlocks the file-based statement-import endpoint. */
    private static final String IMPORT_FEATURE = "DIVIDEND_IMPORT";


    /**
     * Request to fetch dividends directly from IBKR via the Flex Web Service. The {@code token} and
     * {@code queryId} are entered by the user per-request and are NOT stored anywhere.
     */
    public record IbkrFlexRequest(Long accountId, Long ownerId) {}

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

    /** Preview: how many dividends a bulk delete for this owner+account would remove. */
    @GetMapping("/bulk-count")
    public java.util.Map<String, Object> bulkCount(@RequestParam Long ownerId, @RequestParam Long accountId) {
        AppUser user = requireBulkScope(ownerId, accountId);
        long count = dividendService.countForOwnerAccount(user.getId(), ownerId, accountId);
        return java.util.Map.of("dividends", count);
    }

    /** Bulk-delete this user's dividends for one owner+account. Requires the account password. */
    @PostMapping("/bulk-delete")
    public java.util.Map<String, Object> bulkDelete(@RequestBody java.util.Map<String, Object> body) {
        Long ownerId = asLong(body.get("ownerId"));
        Long accountId = asLong(body.get("accountId"));
        AppUser user = requireBulkScope(ownerId, accountId);
        verifyPassword(user, (String) body.get("password"));
        int deleted = dividendService.deleteForOwnerAccount(user.getId(), ownerId, accountId);
        log.info("Bulk-deleted {} dividends for user {} owner {} account {}", deleted, user.getUsername(), ownerId, accountId);
        return java.util.Map.of("deleted", deleted);
    }

    /** Both owner and account are mandatory and must belong to the caller — never a delete-all. */
    private AppUser requireBulkScope(Long ownerId, Long accountId) {
        AppUser user = tenantContext.getCurrentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (ownerId == null || accountId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Both owner and account are required");
        }
        accountRepository.findById(accountId).filter(a -> user.getId().equals(a.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account"));
        ownerRepository.findById(ownerId).filter(o -> user.getId().equals(o.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner"));
        return user;
    }

    private void verifyPassword(AppUser user, String password) {
        if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect password");
        }
    }

    private Long asLong(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.longValue();
        try { return Long.parseLong(o.toString()); } catch (NumberFormatException e) { return null; }
    }

    /**
     * Import a broker dividend statement (IBKR CSV or Saxo XLSX) for the current user.
     * Gated behind the per-user {@code DIVIDEND_IMPORT} feature — callers without it get 403.
     * The format is auto-detected from the file name/extension unless given explicitly.
     */
    @PostMapping("/import")
    public ResponseEntity<DividendImportService.ImportResult> importStatement(
            @RequestParam("file") MultipartFile file,
            @RequestParam Long accountId,
            @RequestParam Long ownerId,
            @RequestParam(required = false) String format) {

        AppUser user = tenantContext.getCurrentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!hasImportFeature(user)) {
            log.warn("User {} attempted dividend import without the {} feature", user.getUsername(), IMPORT_FEATURE);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Dividend import is not enabled for your account");
        }

        // Resolve account & owner, enforcing tenant ownership.
        Account account = accountRepository.findById(accountId)
                .filter(a -> user.getId().equals(a.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account"));
        Owner owner = ownerRepository.findById(ownerId)
                .filter(o -> user.getId().equals(o.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner"));

        DividendImportService.Format fmt;
        try {
            fmt = detectFormat(format, file.getOriginalFilename(), file.getBytes());
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
        try {
            var result = dividendImportService.importFile(file.getBytes(), fmt, user.getId(), account, owner);
            log.info("Imported {} dividends ({} assets created) for user {}", result.imported(), result.assetsCreated(), user.getUsername());
            return ResponseEntity.ok(result);
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded file");
        }
    }

    /**
     * Fetch dividends directly from IBKR using the Flex Web Service. Same feature gate and tenant
     * checks as the file import. The Flex token/queryId are used only for this call and are never
     * stored or logged.
     */
    @PostMapping("/fetch-ibkr")
    public ResponseEntity<DividendImportService.ImportResult> fetchFromIbkr(@RequestBody IbkrFlexRequest req) {
        AppUser user = tenantContext.getCurrentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!com.myfinance.security.FeatureFlags.hasBrokerSync(user)) {
            log.warn("User {} attempted IBKR Flex fetch without the broker-sync feature", user.getUsername());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Broker sync is not enabled for your account");
        }
        Account account = accountRepository.findById(req.accountId())
                .filter(a -> user.getId().equals(a.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account"));
        Owner owner = ownerRepository.findById(req.ownerId())
                .filter(o -> user.getId().equals(o.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner"));

        // Load the account's stored IBKR Flex credential (decrypted only here; never logged).
        var cred = brokerCredentialService.decryptFor(user.getId(), req.accountId(), com.myfinance.model.enums.Broker.IBKR)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No IBKR credentials saved for this account — add them on the Account page"));
        if (cred.secret1() == null || cred.secret1().isBlank() || cred.meta1() == null || cred.meta1().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "IBKR credentials are incomplete — set both the Flex token and Query ID on the Account page");
        }

        try {
            String xml = ibkrFlexService.fetchStatementXml(cred.secret1(), cred.meta1());
            var result = dividendImportService.importFlex(xml, user.getId(), account, owner);
            log.info("IBKR Flex fetch imported {} dividends ({} assets created) for user {}",
                    result.imported(), result.assetsCreated(), user.getUsername());
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private boolean hasImportFeature(AppUser user) { return hasFeature(user, IMPORT_FEATURE); }

    /** True if the user has the given feature (empty CSV = all features enabled, per convention). */
    private boolean hasFeature(AppUser user, String key) {
        String csv = user.getEnabledFeatures();
        if (csv == null || csv.isBlank()) return true;
        for (String f : csv.split(",")) if (key.equals(f.trim())) return true;
        return false;
    }

    private DividendImportService.Format detectFormat(String explicit, String filename, byte[] content) {
        if (explicit != null) {
            try { return DividendImportService.Format.valueOf(explicit.trim().toUpperCase()); }
            catch (IllegalArgumentException ignored) { /* fall through to auto-detect */ }
        }
        String name = filename == null ? "" : filename.toLowerCase();
        if (name.endsWith(".xlsx") || name.contains("saxo")) return DividendImportService.Format.SAXO_XLSX;
        // Both IBKR and Tiger are .csv — sniff the header to tell them apart.
        String head = new String(content, 0, Math.min(content.length, 2000), java.nio.charset.StandardCharsets.UTF_8);
        if (head.contains("Activity Statement") || head.startsWith("Statement") || name.contains("statement")) {
            return DividendImportService.Format.TIGER_CSV;
        }
        return DividendImportService.Format.IBKR_CSV;
    }
}
