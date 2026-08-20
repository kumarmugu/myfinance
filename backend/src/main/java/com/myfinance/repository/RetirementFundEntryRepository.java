package com.myfinance.repository;

import com.myfinance.model.RetirementFundEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RetirementFundEntryRepository extends JpaRepository<RetirementFundEntry, Long> {
    List<RetirementFundEntry> findAllByOrderByEntryDateDesc();
    List<RetirementFundEntry> findByFundTypeOrderByEntryDateDesc(String fundType);
    List<RetirementFundEntry> findByOwnerIdOrderByEntryDateDesc(Long ownerId);
    List<RetirementFundEntry> findByFundTypeAndYearOrderByEntryDateAsc(String fundType, Integer year);

    @Query("SELECT r.fundType, r.entryType, SUM(r.amount) FROM RetirementFundEntry r GROUP BY r.fundType, r.entryType")
    List<Object[]> summaryByFundAndType();

    @Query("SELECT r.year, SUM(r.amount) FROM RetirementFundEntry r WHERE r.fundType = :fundType AND r.entryType = 'CONTRIBUTION' GROUP BY r.year ORDER BY r.year")
    List<Object[]> contributionsByYear(String fundType);
}
