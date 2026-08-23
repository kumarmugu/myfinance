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
    List<SalaryRecord> findByUserIdAndYearOrderByMonthAsc(Long userId, Integer year);
    List<SalaryRecord> findByCountryOrderByYearDescMonthDesc(String country);
    List<SalaryRecord> findByUserIdAndCountryOrderByYearDescMonthDesc(Long userId, String country);

    @Query("SELECT s.year, SUM(s.amount) FROM SalaryRecord s GROUP BY s.year ORDER BY s.year")
    List<Object[]> sumByYear();

    @Query("SELECT s.year, SUM(s.amount) FROM SalaryRecord s WHERE s.userId = :userId GROUP BY s.year ORDER BY s.year")
    List<Object[]> sumByYearForUser(Long userId);

    @Query("SELECT s.year, SUM(s.amount) FROM SalaryRecord s WHERE s.isBonus = true GROUP BY s.year ORDER BY s.year")
    List<Object[]> bonusByYear();

    @Query("SELECT s.year, SUM(s.amount) FROM SalaryRecord s WHERE s.isBonus = true AND s.userId = :userId GROUP BY s.year ORDER BY s.year")
    List<Object[]> bonusByYearForUser(Long userId);

    List<SalaryRecord> findByUserIdOrderByYearDescMonthDesc(Long userId);
}
