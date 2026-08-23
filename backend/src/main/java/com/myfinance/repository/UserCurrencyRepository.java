package com.myfinance.repository;

import com.myfinance.model.UserCurrency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserCurrencyRepository extends JpaRepository<UserCurrency, Long> {
    List<UserCurrency> findByUserIdOrderByCodeAsc(Long userId);
    Optional<UserCurrency> findByUserIdAndCode(Long userId, String code);
    boolean existsByUserIdAndCode(Long userId, String code);
}
