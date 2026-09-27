package com.myfinance.repository;

import com.myfinance.model.LevEtfStrategy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LevEtfStrategyRepository extends JpaRepository<LevEtfStrategy, Long> {
    List<LevEtfStrategy> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<LevEtfStrategy> findByUserIdAndArchivedFalseOrderByCreatedAtDesc(Long userId);
}
