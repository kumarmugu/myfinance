package com.myfinance.repository;

import com.myfinance.model.InsuranceBonusEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InsuranceBonusEntryRepository extends JpaRepository<InsuranceBonusEntry, Long> {
    List<InsuranceBonusEntry> findByPolicyIdOrderByYearNumberAsc(Long policyId);
    void deleteByPolicyId(Long policyId);
}
