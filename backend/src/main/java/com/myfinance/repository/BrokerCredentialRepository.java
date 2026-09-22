package com.myfinance.repository;

import com.myfinance.model.BrokerCredential;
import com.myfinance.model.enums.Broker;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrokerCredentialRepository extends JpaRepository<BrokerCredential, Long> {
    List<BrokerCredential> findByUserId(Long userId);
    Optional<BrokerCredential> findByUserIdAndAccountIdAndBroker(Long userId, Long accountId, Broker broker);
}
