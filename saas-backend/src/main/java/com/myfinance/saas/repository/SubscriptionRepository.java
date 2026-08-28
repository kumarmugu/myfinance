package com.myfinance.saas.repository;

import com.myfinance.saas.domain.Subscription;
import com.myfinance.saas.domain.enums.SubscriptionState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByCustomerId(Long customerId);
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    List<Subscription> findByState(SubscriptionState state);

    /** Trials that have passed their end date and are still in TRIAL — for the scheduled sweep. */
    List<Subscription> findByStateAndTrialEndsAtBefore(SubscriptionState state, LocalDateTime cutoff);

    /** Trials ending within a window — for "trial ending soon" notifications. */
    List<Subscription> findByStateAndTrialEndsAtBetween(SubscriptionState state, LocalDateTime start, LocalDateTime end);
}
