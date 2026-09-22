package com.myfinance.controller;

import com.myfinance.model.AppUser;
import com.myfinance.model.enums.Broker;
import com.myfinance.security.FeatureFlags;
import com.myfinance.security.TenantContext;
import com.myfinance.service.BrokerCredentialService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Manage stored broker API credentials. Gated by the per-user broker-sync feature. Secrets are
 * write-only: they are encrypted on save and NEVER returned — GET responses expose only masked
 * status (which fields are set, non-secret identifiers, last-updated).
 */
@RestController
@RequestMapping("/api/broker-credentials")
@RequiredArgsConstructor
@Slf4j
public class BrokerCredentialController {

    private final BrokerCredentialService service;
    private final TenantContext tenantContext;
    private final com.myfinance.repository.AccountRepository accountRepository;
    private final com.myfinance.repository.OwnerRepository ownerRepository;

    /** Save/update request. secret1/secret2 optional on update (blank = keep existing). */
    public record SaveRequest(String broker, Long ownerId, Long accountId,
                              String meta1, String meta2, String secret1, String secret2) {}

    @GetMapping
    public Map<String, Object> list() {
        AppUser user = requireFeature();
        return Map.of("encryptionEnabled", service.encryptionEnabled(),
                "credentials", service.listStatuses(user.getId()));
    }

    @PostMapping
    public BrokerCredentialService.CredentialStatus save(@RequestBody SaveRequest req) {
        AppUser user = requireFeature();
        if (!service.encryptionEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Credential storage is not configured on the server (set the encryption key)");
        }
        Broker broker = parseBroker(req.broker());
        Long accountId = req.accountId();
        Long ownerId = req.ownerId();
        if (accountId == null || ownerId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner and account are required");
        }
        // Tenant checks: the account and owner must belong to the caller.
        accountRepository.findById(accountId).filter(a -> user.getId().equals(a.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account"));
        ownerRepository.findById(ownerId).filter(o -> user.getId().equals(o.getUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid owner"));
        return service.save(user.getId(), ownerId, accountId, broker,
                req.meta1(), req.meta2(), req.secret1(), req.secret2());
    }

    @DeleteMapping
    public void delete(@RequestParam String broker, @RequestParam Long accountId) {
        AppUser user = requireFeature();
        service.delete(user.getId(), accountId, parseBroker(broker));
    }

    private Broker parseBroker(String b) {
        try { return Broker.valueOf(b == null ? "" : b.trim().toUpperCase()); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown broker"); }
    }

    private AppUser requireFeature() {
        AppUser user = tenantContext.getCurrentUser();
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (!FeatureFlags.hasBrokerSync(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Broker sync is not enabled for your account");
        }
        return user;
    }
}
