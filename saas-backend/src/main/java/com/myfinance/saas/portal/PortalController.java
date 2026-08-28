package com.myfinance.saas.portal;

import com.myfinance.saas.auth.CustomerPrincipal;
import com.myfinance.saas.payment.CheckoutSession;
import com.myfinance.saas.portal.dto.CheckoutRequestDto;
import com.myfinance.saas.portal.dto.PaymentView;
import com.myfinance.saas.portal.dto.SubscriptionView;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Authenticated customer portal (billing/subscription). Requires ROLE_CUSTOMER (enforced by
 * SecurityConfig for /api/portal/**). The customer id is ALWAYS taken from the authenticated
 * principal, never from the request, so a customer can only act on their own resources.
 *
 * This portal grants NO access to the finance app's pages or internal APIs.
 */
@RestController
@RequestMapping("/api/portal")
@RequiredArgsConstructor
@Tag(name = "Portal", description = "Authenticated customer billing/subscription portal")
public class PortalController {

    private final PortalService portalService;

    @GetMapping("/subscription")
    public ResponseEntity<SubscriptionView> subscription(@AuthenticationPrincipal CustomerPrincipal principal) {
        return ResponseEntity.ok(portalService.getSubscription(principal.customerId()));
    }

    @GetMapping("/payments")
    public ResponseEntity<List<PaymentView>> payments(@AuthenticationPrincipal CustomerPrincipal principal) {
        return ResponseEntity.ok(portalService.getPaymentHistory(principal.customerId()));
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, String>> checkout(@AuthenticationPrincipal CustomerPrincipal principal,
                                                        @Valid @RequestBody CheckoutRequestDto request) {
        CheckoutSession session = portalService.startCheckout(
                principal.customerId(), request.getPlanCode(), request.getMethod());
        return ResponseEntity.ok(Map.of(
                "sessionId", session.sessionId(),
                "redirectUrl", session.redirectUrl()));
    }

    @PostMapping("/cancel")
    public ResponseEntity<Map<String, Object>> cancel(@AuthenticationPrincipal CustomerPrincipal principal,
                                                       @RequestParam(value = "atPeriodEnd", defaultValue = "true") boolean atPeriodEnd) {
        portalService.cancelSubscription(principal.customerId(), atPeriodEnd);
        return ResponseEntity.ok(Map.of("message", "Cancellation requested", "atPeriodEnd", atPeriodEnd));
    }

    @ExceptionHandler(PortalException.class)
    public ResponseEntity<Map<String, Object>> handlePortalError(PortalException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", ex.getMessage()));
    }
}
