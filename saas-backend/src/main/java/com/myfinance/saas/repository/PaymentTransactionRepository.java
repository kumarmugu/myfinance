package com.myfinance.saas.repository;

import com.myfinance.saas.domain.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
    List<PaymentTransaction> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}
