package com.myfinance.repository;

import com.myfinance.model.LevEtfBacktest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LevEtfBacktestRepository extends JpaRepository<LevEtfBacktest, Long> {
    List<LevEtfBacktest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
