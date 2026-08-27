package com.myfinance.repository;

import com.myfinance.model.BudgetAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetAllocationRepository extends JpaRepository<BudgetAllocation, Long> {
    List<BudgetAllocation> findByBudgetPlanId(Long budgetPlanId);
    void deleteByBudgetPlanId(Long budgetPlanId);
}
