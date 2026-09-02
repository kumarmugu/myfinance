package com.myfinance.repository;

import com.myfinance.model.CurrencyRate;
import com.myfinance.model.enums.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CurrencyRateRepository extends JpaRepository<CurrencyRate, Long> {
    Optional<CurrencyRate> findTopByFromCurrencyAndToCurrencyOrderByEffectiveDateDesc(Currency from, Currency to);
    List<CurrencyRate> findByUserId(Long userId);

    /** One rate per pair per user: used to upsert instead of accumulating history rows. */
    Optional<CurrencyRate> findByUserIdAndFromCurrencyAndToCurrency(Long userId, String fromCurrency, String toCurrency);
}
