package com.myfinance.repository;

import com.myfinance.model.Dividend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DividendRepository extends JpaRepository<Dividend, Long> {
    List<Dividend> findByOwnerIdOrderByReceivedDateDesc(Long ownerId);
    List<Dividend> findByYear(Integer year);
    List<Dividend> findByAccountIdOrderByReceivedDateDesc(Long accountId);
    List<Dividend> findByAssetId(Long assetId);
    List<Dividend> findAllByOrderByReceivedDateDesc();

    @Query("SELECT d.year, SUM(d.amount) FROM Dividend d GROUP BY d.year ORDER BY d.year")
    List<Object[]> sumByYear();

    List<Dividend> findByUserIdOrderByReceivedDateDesc(Long userId);

    @Query("SELECT d.year, SUM(d.amount) FROM Dividend d WHERE d.userId = :userId GROUP BY d.year ORDER BY d.year")
    List<Object[]> sumByYearForUser(@Param("userId") Long userId);
}
