package com.myfinance.saas.repository;

import com.myfinance.saas.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<Customer> findByStripeCustomerId(String stripeCustomerId);

    /** Customers not yet provisioned into the finance app — for reconciliation retries. */
    java.util.List<Customer> findByProvisionedFalse();
}
