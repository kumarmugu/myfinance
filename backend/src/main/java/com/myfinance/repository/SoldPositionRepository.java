package com.myfinance.repository;

import com.myfinance.model.SoldPosition;
import com.myfinance.model.enums.InvestmentPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SoldPositionRepository extends JpaRepository<SoldPosition, Long> {
    List<SoldPosition> findByOwnerIdOrderBySoldDateDesc(Long ownerId);
    List<SoldPosition> findByAccountIdOrderBySoldDateDesc(Long accountId);
    List<SoldPosition> findByIsShortTermTrueOrderBySoldDateDesc();
    List<SoldPosition> findAllByOrderBySoldDateDesc();

    @Query("SELECT sp FROM SoldPosition sp WHERE sp.isShortTerm = true OR sp.purpose = 'TRADING' OR sp.purpose = 'SHORT_TERM' ORDER BY sp.soldDate DESC")
    List<SoldPosition> findShortTermTrades();

    List<SoldPosition> findByUserIdOrderBySoldDateDesc(Long userId);

    /** The auto-generated sold position for a given SELL transaction (for edit/delete sync). */
    java.util.Optional<SoldPosition> findBySourceTransactionId(Long sourceTransactionId);

    @Query("SELECT sp FROM SoldPosition sp WHERE sp.userId = :userId AND (sp.isShortTerm = true OR sp.purpose = 'TRADING' OR sp.purpose = 'SHORT_TERM') ORDER BY sp.soldDate DESC")
    List<SoldPosition> findShortTermTradesByUser(@Param("userId") Long userId);
}
