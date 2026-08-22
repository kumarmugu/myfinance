package com.myfinance.repository;

import com.myfinance.model.InsurancePolicy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {
    List<InsurancePolicy> findByIsActiveTrueOrderByPolicyNameAsc();
    List<InsurancePolicy> findByOwnerIdOrderByPolicyNameAsc(Long ownerId);
    List<InsurancePolicy> findByUserIdAndIsActiveTrue(Long userId);
}
