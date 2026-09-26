package com.myfinance.repository;

import com.myfinance.model.StockSplit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface StockSplitRepository extends JpaRepository<StockSplit, Long> {
    List<StockSplit> findByUserId(Long userId);
    Optional<StockSplit> findByUserIdAndSymbolIgnoreCaseAndEffectiveDate(Long userId, String symbol, LocalDate effectiveDate);
}
