package com.myfinance.repository;

import com.myfinance.model.RebalancePlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RebalancePlanRepository extends JpaRepository<RebalancePlan, Long> {
    List<RebalancePlan> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<RebalancePlan> findByUserIdAndStrategyIdOrderByCreatedAtDesc(Long userId, Long strategyId);
}
