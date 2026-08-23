package com.myfinance.repository;

import com.myfinance.model.FixedDeposit;
import com.myfinance.model.enums.FDStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface FixedDepositRepository extends JpaRepository<FixedDeposit, Long> {
    List<FixedDeposit> findByStatus(FDStatus status);
    List<FixedDeposit> findByHolderId(Long holderId);
    List<FixedDeposit> findByBankId(Long bankId);
    List<FixedDeposit> findByRequiresUpdateTrue();

    @Query("SELECT fd FROM FixedDeposit fd WHERE fd.status = 'ACTIVE' AND fd.maturityDate <= :date")
    List<FixedDeposit> findMaturingBefore(@Param("date") LocalDate date);

    @Query("SELECT fd FROM FixedDeposit fd WHERE fd.status = 'ACTIVE' ORDER BY fd.maturityDate ASC")
    List<FixedDeposit> findAllActiveOrderByMaturity();

    List<FixedDeposit> findByUserId(Long userId);

    List<FixedDeposit> findByUserIdAndStatus(Long userId, FDStatus status);

    List<FixedDeposit> findByUserIdAndHolderId(Long userId, Long holderId);

    List<FixedDeposit> findByUserIdAndBankId(Long userId, Long bankId);

    @Query("SELECT fd FROM FixedDeposit fd WHERE fd.userId = :userId AND fd.status = 'ACTIVE' AND fd.maturityDate <= :date")
    List<FixedDeposit> findMaturingBeforeForUser(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("SELECT fd FROM FixedDeposit fd WHERE fd.userId = :userId AND fd.requiresUpdate = true")
    List<FixedDeposit> findByUserIdAndRequiresUpdateTrue(@Param("userId") Long userId);
}
