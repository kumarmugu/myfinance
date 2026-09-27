package com.myfinance.repository;

import com.myfinance.model.LevEtfPositionSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LevEtfPositionSnapshotRepository extends JpaRepository<LevEtfPositionSnapshot, Long> {
    List<LevEtfPositionSnapshot> findByUserIdAndStrategyId(Long userId, Long strategyId);
    Optional<LevEtfPositionSnapshot> findByUserIdAndExternalId(Long userId, String externalId);
}
