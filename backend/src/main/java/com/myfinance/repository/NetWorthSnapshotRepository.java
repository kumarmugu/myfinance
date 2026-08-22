package com.myfinance.repository;

import com.myfinance.model.NetWorthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, Long> {
    List<NetWorthSnapshot> findAllByOrderBySnapshotDateDesc();
    List<NetWorthSnapshot> findByOwnerIdOrderBySnapshotDateDesc(Long ownerId);
    Optional<NetWorthSnapshot> findBySnapshotDate(LocalDate date);
    Optional<NetWorthSnapshot> findByOwnerIdAndYear(Long ownerId, Integer year);
    Optional<NetWorthSnapshot> findTopByOrderBySnapshotDateDesc();
    List<NetWorthSnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(LocalDate start, LocalDate end);
    List<NetWorthSnapshot> findByUserIdOrderBySnapshotDateDesc(Long userId);
}
