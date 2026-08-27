package com.myfinance.repository;

import com.myfinance.model.BudgetIncome;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BudgetIncomeRepository extends JpaRepository<BudgetIncome, Long> {
    List<BudgetIncome> findByBudgetPlanId(Long budgetPlanId);
    void deleteByBudgetPlanId(Long budgetPlanId);
}
