package com.myfinance.provisioning;

import com.myfinance.provisioning.dto.ProvisionUserRequest;
import com.myfinance.provisioning.dto.ProvisionUserResponse;
import com.myfinance.provisioning.dto.UpdateAccessRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Internal, server-to-server provisioning API used by the separate SaaS platform.
 *
 * Secured by {@link ProvisioningTokenFilter} (X-Provisioning-Token). Not intended for
 * public/browser use and should be network-restricted to the SaaS backend in production.
 * Additive only — existing endpoints and behaviors are unchanged.
 */
@RestController
@RequestMapping("/api/internal/provisioning")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Internal Provisioning", description = "Server-to-server user provisioning for the SaaS platform")
public class ProvisioningController {

    private final ProvisioningService provisioningService;

    /** Idempotently create (or return) a finance-app user for a SaaS customer. */
    @PostMapping("/users")
    public ResponseEntity<ProvisionUserResponse> provisionUser(@Valid @RequestBody ProvisionUserRequest request) {
        ProvisionUserResponse result = provisioningService.provisionUser(request);
        HttpStatus status = result.isCreated() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }

    /** Update a user's access status (active/suspended) and optionally their feature set. */
    @PostMapping("/status")
    public ResponseEntity<ProvisionUserResponse> updateAccess(@Valid @RequestBody UpdateAccessRequest request) {
        return ResponseEntity.ok(provisioningService.updateAccess(request));
    }

    /** Fetch the current provisioning/access status for an email. */
    @GetMapping("/users")
    public ResponseEntity<ProvisionUserResponse> getStatus(@RequestParam("email") String email) {
        return ResponseEntity.ok(provisioningService.getStatusByEmail(email));
    }

    @ExceptionHandler(ProvisioningNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ProvisioningNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", ex.getMessage()));
    }
}
