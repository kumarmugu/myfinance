package com.myfinance.repository;

import com.myfinance.model.BankSavings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BankSavingsRepository extends JpaRepository<BankSavings, Long> {
    List<BankSavings> findByUserIdOrderByAccountNameAsc(Long userId);
    List<BankSavings> findByUserIdAndCountry(Long userId, String country);
    List<BankSavings> findByUserIdAndIncludeInNetWorthTrue(Long userId);
    List<BankSavings> findByUserIdAndOwnerIdOrderByAccountNameAsc(Long userId, Long ownerId);
    List<BankSavings> findByOwnerId(Long ownerId);
}
