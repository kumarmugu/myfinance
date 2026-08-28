package com.myfinance.saas.signup;

import com.myfinance.saas.auth.OneTimeToken;
import com.myfinance.saas.auth.PasswordPolicy;
import com.myfinance.saas.auth.TokenService;
import com.myfinance.saas.config.AppProperties;
import com.myfinance.saas.domain.Customer;
import com.myfinance.saas.domain.Plan;
import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.CustomerStatus;
import com.myfinance.saas.domain.enums.SubscriptionState;
import com.myfinance.saas.email.EmailService;
import com.myfinance.saas.integration.FinanceAppClient;
import com.myfinance.saas.integration.FinanceAppException;
import com.myfinance.saas.repository.CustomerRepository;
import com.myfinance.saas.repository.PlanRepository;
import com.myfinance.saas.repository.SubscriptionRepository;
import com.myfinance.saas.signup.dto.SignupRequest;
import com.myfinance.saas.signup.dto.SignupResponse;
import com.myfinance.saas.subscription.TrialCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Orchestrates self-service signup:
 *   1. Validate + password policy.
 *   2. Create Customer (PENDING_VERIFICATION) and a TRIAL Subscription.
 *   3. Issue an email-verification token and send the verification email.
 *   4. Provision the finance-app user (idempotent, best-effort — failure does not roll back
 *      signup; provisioning is retried later by reconciliation).
 *   5. Send a welcome email.
 *
 * Enumeration-safe: a repeat signup for an existing email re-sends verification and returns
 * the same generic response rather than revealing that the account already exists.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SignupService {

    private static final String DEFAULT_PLAN_CODE = "free_trial";
    private static final String GENERIC_MESSAGE =
            "Account created. Please check your email to verify your address.";

    private final CustomerRepository customerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final PasswordPolicy passwordPolicy;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final FinanceAppClient financeAppClient;
    private final TrialCalculator trialCalculator;
    private final AppProperties appProperties;
    private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        String email = normalize(request.getEmail());

        // Password policy is enforced server-side regardless of frontend checks.
        passwordPolicy.validate(request.getPassword())
                .ifPresent(msg -> { throw new IllegalArgumentException(msg); });

        Optional<Customer> existing = customerRepository.findByEmail(email);
        if (existing.isPresent()) {
            // Enumeration-safe: don't reveal existence. If still unverified, resend verification.
            Customer c = existing.get();
            if (!c.isEmailVerified()) {
                sendVerification(c);
            }
            log.info("Signup repeat for existing email(hash)={}", emailHash(email));
            return SignupResponse.builder().message(GENERIC_MESSAGE).provisioned(c.isProvisioned()).build();
        }

        Plan plan = resolvePlan(request.getPlanCode());

        Customer customer = customerRepository.save(Customer.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .status(CustomerStatus.PENDING_VERIFICATION)
                .emailVerified(false)
                .termsAccepted(request.isAcceptTerms())
                .termsAcceptedAt(request.isAcceptTerms() ? LocalDateTime.now() : null)
                .build());

        int trialDays = plan.getTrialDays() > 0 ? plan.getTrialDays() : appProperties.getTrial().getDays();
        LocalDateTime now = LocalDateTime.now();
        subscriptionRepository.save(Subscription.builder()
                .customer(customer)
                .plan(plan)
                .state(SubscriptionState.TRIAL)
                .trialEndsAt(trialCalculator.trialEnd(now, trialDays))
                .build());

        sendVerification(customer);
        emailService.sendTrialStarted(customer.getEmail(), trialDays, appProperties.getFinanceAppLoginUrl());

        boolean provisioned = tryProvision(customer, plan);

        log.info("Signup complete customerId={} plan={} trialDays={} provisioned={}",
                customer.getId(), plan.getCode(), trialDays, provisioned);
        return SignupResponse.builder().message(GENERIC_MESSAGE).provisioned(provisioned).build();
    }

    /**
     * Attempt to provision the finance-app user. Best-effort: on failure we log and leave
     * {@code provisioned=false} so a reconciliation job can retry. Signup is NOT rolled back.
     */
    private boolean tryProvision(Customer customer, Plan plan) {
        if (!financeAppClient.isConfigured()) {
            log.warn("Finance app provisioning skipped (not configured) for customerId={}", customer.getId());
            return false;
        }
        try {
            financeAppClient.provisionUser(customer.getEmail(), customer.getFullName(), plan.getEnabledFeatures());
            customer.setProvisioned(true);
            customerRepository.save(customer);
            emailService.sendWelcomeEmail(customer.getEmail(), appProperties.getFinanceAppLoginUrl());
            return true;
        } catch (FinanceAppException e) {
            log.error("Provisioning failed for customerId={}, will retry later: {}", customer.getId(), e.getMessage());
            return false;
        }
    }

    private void sendVerification(Customer customer) {
        String raw = tokenService.issue(customer.getId(), OneTimeToken.Purpose.EMAIL_VERIFICATION);
        String url = appProperties.getPublicWebUrl() + "/verify-email?token=" + raw;
        emailService.sendVerificationEmail(customer.getEmail(), url);
    }

    private Plan resolvePlan(String planCode) {
        String code = (planCode == null || planCode.isBlank()) ? DEFAULT_PLAN_CODE : planCode;
        return planRepository.findByCode(code)
                .filter(Plan::isActive)
                .orElseGet(() -> planRepository.findByCode(DEFAULT_PLAN_CODE)
                        .orElseThrow(() -> new IllegalStateException("No default plan configured")));
    }

    private String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    private String emailHash(String email) {
        return email == null ? "null" : Integer.toHexString(email.hashCode());
    }
}
