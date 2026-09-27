package com.myfinance.repository;

import com.myfinance.model.LevEtfInstrument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LevEtfInstrumentRepository extends JpaRepository<LevEtfInstrument, Long> {
    List<LevEtfInstrument> findByUserId(Long userId);
    List<LevEtfInstrument> findByUserIdAndEnabledTrue(Long userId);
    List<LevEtfInstrument> findByUserIdAndUnderlyingBenchmarkId(Long userId, Long underlyingBenchmarkId);
}
