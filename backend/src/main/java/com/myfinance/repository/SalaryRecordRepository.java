package com.myfinance.repository;

import com.myfinance.model.SalaryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SalaryRecordRepository extends JpaRepository<SalaryRecord, Long> {
    List<SalaryRecord> findAllByOrderByYearDescMonthDesc();
    List<SalaryRecord> findByYearOrderByMonthAsc(Integer year);
    List<SalaryRecord> findByCountryOrderByYearDescMonthDesc(String country);

    @Query("SELECT s.year, SUM(s.amount) FROM SalaryRecord s GROUP BY s.year ORDER BY s.year")
    List<Object[]> sumByYear();

    @Query("SELECT s.year, SUM(s.amount) FROM SalaryRecord s WHERE s.isBonus = true GROUP BY s.year ORDER BY s.year")
    List<Object[]> bonusByYear();
}
