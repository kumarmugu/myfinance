package com.myfinance.saas.payment.webhook;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Receives payment provider webhooks. The raw request body is required for signature
 * verification, so the body is consumed as a String (not re-serialized).
 *
 * Security: unverified/tampered events are rejected with 400 and never processed. Duplicate
 * events are safely ignored (idempotent). Subscription activation happens ONLY here from a
 * verified event — never from browser-reported success.
 */
@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Payment provider webhooks (signature-verified)")
public class WebhookController {

    private final WebhookVerifier webhookVerifier;
    private final WebhookService webhookService;

    @PostMapping("/stripe")
    public ResponseEntity<?> stripe(@RequestBody String rawPayload,
                                    @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        if (signature == null || signature.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing signature"));
        }

        Optional<WebhookEvent> verified = webhookVerifier.verifyAndParse(rawPayload, signature);
        if (verified.isEmpty()) {
            // Fail closed: invalid signature or untrusted payload.
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Invalid webhook"));
        }

        try {
            webhookService.process(verified.get());
            // Always 200 for verified events (even duplicates) so the provider stops retrying.
            return ResponseEntity.ok(Map.of("received", true));
        } catch (Exception e) {
            // Transient handler failure: 500 so the provider retries later.
            log.error("Webhook processing error for event {}: {}", verified.get().getEventId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Processing error"));
        }
    }
}
