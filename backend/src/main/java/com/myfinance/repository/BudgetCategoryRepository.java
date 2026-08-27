package com.myfinance.repository;

import com.myfinance.model.BudgetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BudgetCategoryRepository extends JpaRepository<BudgetCategory, Long> {
    List<BudgetCategory> findByUserIdAndIsActiveTrueOrderBySortOrderAsc(Long userId);
    List<BudgetCategory> findByUserIdOrderBySortOrderAsc(Long userId);
    Optional<BudgetCategory> findByUserIdAndName(Long userId, String name);
    boolean existsByUserIdAndName(Long userId, String name);
}
