package com.myfinance.repository;

import com.myfinance.model.AllocationSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AllocationSnapshotRepository extends JpaRepository<AllocationSnapshot, Long> {
    List<AllocationSnapshot> findByUserIdAndStrategyIdOrderByCalculationTimestampDesc(Long userId, Long strategyId);
    Optional<AllocationSnapshot> findFirstByUserIdAndStrategyIdOrderByCalculationTimestampDesc(Long userId, Long strategyId);
}
