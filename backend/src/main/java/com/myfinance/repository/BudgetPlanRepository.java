package com.myfinance.repository;

import com.myfinance.model.BudgetPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetPlanRepository extends JpaRepository<BudgetPlan, Long> {
    Optional<BudgetPlan> findByUserIdAndYearAndMonth(Long userId, Integer year, Integer month);
    List<BudgetPlan> findByUserIdAndYearOrderByMonthAsc(Long userId, Integer year);
    List<BudgetPlan> findByUserIdOrderByYearDescMonthDesc(Long userId);
}
