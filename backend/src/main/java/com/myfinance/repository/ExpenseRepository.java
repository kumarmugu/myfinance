package com.myfinance.repository;

import com.myfinance.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByUserIdOrderByExpenseDateDesc(Long userId);

    List<Expense> findByUserIdAndCategoryId(Long userId, Long categoryId);

    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND e.expenseDate >= :start AND e.expenseDate <= :end ORDER BY e.expenseDate DESC")
    List<Expense> findByUserIdAndDateRange(@Param("userId") Long userId, @Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT e FROM Expense e WHERE e.userId = :userId AND YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) = :month ORDER BY e.expenseDate DESC")
    List<Expense> findByUserIdAndYearMonth(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);

    @Query("SELECT e.category.id, SUM(e.amount) FROM Expense e WHERE e.userId = :userId AND YEAR(e.expenseDate) = :year AND MONTH(e.expenseDate) = :month GROUP BY e.category.id")
    List<Object[]> sumByCategoryForMonth(@Param("userId") Long userId, @Param("year") int year, @Param("month") int month);
}
