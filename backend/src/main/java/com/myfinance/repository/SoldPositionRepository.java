package com.myfinance.repository;

import com.myfinance.model.SoldPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SoldPositionRepository extends JpaRepository<SoldPosition, Long> {
    List<SoldPosition> findByOwnerIdOrderBySoldDateDesc(Long ownerId);
    List<SoldPosition> findByAccountIdOrderBySoldDateDesc(Long accountId);
    List<SoldPosition> findByIsShortTermTrueOrderBySoldDateDesc();
    List<SoldPosition> findAllByOrderBySoldDateDesc();
}
