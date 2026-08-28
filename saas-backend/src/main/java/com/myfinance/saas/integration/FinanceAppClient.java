package com.myfinance.saas.integration;

import com.myfinance.saas.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-to-server client for the existing MyFinance app's internal provisioning API.
 *
 * Sends the shared service token in the X-Provisioning-Token header. Never exposes the token
 * to the browser. All calls are idempotent on the finance-app side (keyed by email), so retries
 * are safe.
 */
@Component
@Slf4j
public class FinanceAppClient {

    private static final String TOKEN_HEADER = "X-Provisioning-Token";

    private final RestClient restClient;
    private final String token;

    public FinanceAppClient(AppProperties appProperties) {
        this.token = appProperties.getFinanceApp().getProvisioningToken();
        this.restClient = RestClient.builder()
                .baseUrl(appProperties.getFinanceApp().getBaseUrl())
                .build();
    }

    public boolean isConfigured() {
        return token != null && !token.isBlank();
    }

    /**
     * Idempotently create a finance-app user for a customer.
     *
     * @return the provisioning result
     * @throws FinanceAppException on transport/HTTP errors so callers can retry/queue.
     */
    public ProvisionResult provisionUser(String email, String fullName, String enabledFeatures) {
        requireConfigured();
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        if (fullName != null) body.put("displayName", fullName);
        body.put("enabledFeatures", enabledFeatures != null ? enabledFeatures : "");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/api/internal/provisioning/users")
                    .header(TOKEN_HEADER, token)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new FinanceAppException("Provisioning failed with status " + res.getStatusCode());
                    })
                    .body(Map.class);

            Long userId = response != null && response.get("userId") != null
                    ? Long.valueOf(response.get("userId").toString()) : null;
            boolean created = response != null && Boolean.TRUE.equals(response.get("created"));
            log.info("Provisioned finance user (created={}) for email(hash)={}", created, emailHash(email));
            return new ProvisionResult(userId, created);
        } catch (FinanceAppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Finance app provisioning call failed: {}", e.getMessage());
            throw new FinanceAppException("Unable to reach finance app for provisioning");
        }
    }

    /**
     * Update a finance-app user's access status (active/suspended) and optionally features.
     */
    public void updateAccess(String email, boolean active, String enabledFeatures) {
        requireConfigured();
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("active", active);
        if (enabledFeatures != null) body.put("enabledFeatures", enabledFeatures);

        try {
            restClient.post()
                    .uri("/api/internal/provisioning/status")
                    .header(TOKEN_HEADER, token)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        throw new FinanceAppException("Access update failed with status " + res.getStatusCode());
                    })
                    .toBodilessEntity();
            log.info("Updated finance access (active={}) for email(hash)={}", active, emailHash(email));
        } catch (FinanceAppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Finance app access update failed: {}", e.getMessage());
            throw new FinanceAppException("Unable to reach finance app for access update");
        }
    }

    private void requireConfigured() {
        if (!isConfigured()) {
            throw new FinanceAppException("Provisioning token is not configured");
        }
    }

    private String emailHash(String email) {
        return email == null ? "null" : Integer.toHexString(email.hashCode());
    }

    /** Result of a provisioning call. */
    public record ProvisionResult(Long userId, boolean created) {}
}
