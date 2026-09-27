package com.myfinance.repository;

import com.myfinance.model.BenchmarkIndex;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BenchmarkIndexRepository extends JpaRepository<BenchmarkIndex, Long> {
    List<BenchmarkIndex> findByUserId(Long userId);
    List<BenchmarkIndex> findByUserIdAndEnabledTrue(Long userId);
}
