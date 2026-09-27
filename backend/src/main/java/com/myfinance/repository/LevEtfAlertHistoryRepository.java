package com.myfinance.repository;

import com.myfinance.model.LevEtfAlertHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevEtfAlertHistoryRepository extends JpaRepository<LevEtfAlertHistory, Long> {
    List<LevEtfAlertHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<LevEtfAlertHistory> findByUserIdAndDedupeKey(Long userId, String dedupeKey);
    long countByUserIdAndReadFlagFalse(Long userId);
}
