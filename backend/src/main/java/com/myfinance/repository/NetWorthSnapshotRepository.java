package com.myfinance.repository;

import com.myfinance.model.NetWorthSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface NetWorthSnapshotRepository extends JpaRepository<NetWorthSnapshot, Long> {
    Optional<NetWorthSnapshot> findBySnapshotDate(LocalDate date);
    List<NetWorthSnapshot> findBySnapshotDateBetweenOrderBySnapshotDateAsc(LocalDate start, LocalDate end);
    List<NetWorthSnapshot> findAllByOrderBySnapshotDateDesc();
    Optional<NetWorthSnapshot> findTopByOrderBySnapshotDateDesc();
}
