package com.myfinance.repository;

import com.myfinance.model.Transaction;
import com.myfinance.model.enums.TransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findAllByOrderByTransactionDateDesc();
    List<Transaction> findByOwnerIdOrderByTransactionDateDesc(Long ownerId);
    List<Transaction> findByAccountIdOrderByTransactionDateDesc(Long accountId);
    List<Transaction> findByAssetIdOrderByTransactionDateDesc(Long assetId);
    List<Transaction> findByTransactionType(TransactionType type);
    List<Transaction> findByTransactionDateBetweenOrderByTransactionDateDesc(LocalDate start, LocalDate end);

    @Query("SELECT t FROM Transaction t WHERE t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    List<Transaction> findRecentTransactions(@Param("startDate") LocalDate startDate);

    List<Transaction> findByUserIdOrderByTransactionDateDesc(Long userId);

    List<Transaction> findByUserIdAndAccountIdOrderByTransactionDateDesc(Long userId, Long accountId);

    List<Transaction> findByUserIdAndAssetIdOrderByTransactionDateDesc(Long userId, Long assetId);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.transactionDate BETWEEN :start AND :end ORDER BY t.transactionDate DESC")
    List<Transaction> findByUserIdAndDateRange(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT t FROM Transaction t WHERE t.userId = :userId AND t.transactionDate >= :startDate ORDER BY t.transactionDate DESC")
    List<Transaction> findRecentByUser(@Param("userId") Long userId, @Param("startDate") LocalDate startDate);
}
