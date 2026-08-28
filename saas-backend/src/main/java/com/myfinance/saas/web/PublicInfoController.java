package com.myfinance.saas.web;

import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.portal.dto.PlanView;
import com.myfinance.saas.repository.PlanRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public, non-sensitive configuration for the saas-web frontend.
 * Exposes only values that are safe for the browser (e.g. the Stripe PUBLISHABLE key,
 * the finance-app login URL, trial length). Never exposes secrets.
 */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Public", description = "Public, non-sensitive configuration and content")
public class PublicInfoController {

    private final AppProperties appProperties;
    private final PlanRepository planRepository;

    /** Public list of active plans for the pricing page (non-sensitive fields only). */
    @GetMapping("/plans")
    public ResponseEntity<List<PlanView>> plans() {
        List<PlanView> plans = planRepository.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(PlanView::from)
                .toList();
        return ResponseEntity.ok(plans);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("trialDays", appProperties.getTrial().getDays());
        cfg.put("paymentProvider", appProperties.getPayment().getProvider());
        // Publishable key is safe to expose to the browser; secret key is never returned.
        cfg.put("stripePublishableKey", appProperties.getStripe().getPublishableKey());
        cfg.put("loginUrl", appProperties.getFinanceAppLoginUrl());
        return ResponseEntity.ok(cfg);
    }
}
