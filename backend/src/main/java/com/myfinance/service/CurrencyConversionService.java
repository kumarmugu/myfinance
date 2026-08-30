package com.myfinance.service;

import com.myfinance.model.CurrencyRate;
import com.myfinance.repository.CurrencyRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Converts monetary amounts between currencies using the user-maintained
 * {@link CurrencyRate} entries. The net-worth base currency is SGD, so all
 * asset/liability values are normalised to SGD before being summed.
 *
 * Resolution order for a factor {@code from -> SGD}:
 *   1. identity when {@code from} already equals the base (SGD)
 *   2. a direct rate {@code from -> SGD} (latest by effectiveDate)
 *   3. the inverse of a {@code SGD -> from} rate (latest by effectiveDate)
 *   4. fallback of 1.0 (amount summed as-is) with a warning — never throws
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyConversionService {

    public static final String BASE_CURRENCY = "SGD";

    private final CurrencyRateRepository currencyRateRepository;

    /**
     * Convert {@code amount} in {@code fromCurrency} to the base currency (SGD)
     * for the given user. Null/blank inputs are treated as zero / base currency.
     */
    public BigDecimal toBase(BigDecimal amount, String fromCurrency, Long userId) {
        if (amount == null) return BigDecimal.ZERO;
        BigDecimal factor = factorToBase(fromCurrency, userId);
        return amount.multiply(factor);
    }

    /**
     * The multiplier that converts one unit of {@code fromCurrency} into the base
     * currency (SGD) for this user. Returns 1 when the currency is the base or
     * unknown / no rate is configured.
     */
    public BigDecimal factorToBase(String fromCurrency, Long userId) {
        String from = fromCurrency == null ? "" : fromCurrency.trim().toUpperCase();
        if (from.isEmpty() || BASE_CURRENCY.equals(from)) {
            return BigDecimal.ONE;
        }

        List<CurrencyRate> rates = currencyRateRepository.findByUserId(userId);

        // 1. direct: from -> SGD (latest effectiveDate wins)
        BigDecimal direct = latestRate(rates, from, BASE_CURRENCY);
        if (direct != null && direct.compareTo(BigDecimal.ZERO) > 0) {
            return direct;
        }

        // 2. inverse: SGD -> from  =>  1 / rate
        BigDecimal inverse = latestRate(rates, BASE_CURRENCY, from);
        if (inverse != null && inverse.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ONE.divide(inverse, 10, RoundingMode.HALF_UP);
        }

        log.warn("No FX rate {}->{} (or inverse) for userId={}; summing amount without conversion",
                from, BASE_CURRENCY, userId);
        return BigDecimal.ONE;
    }

    /**
     * The multiplier that converts one unit of the base currency (SGD) into
     * {@code toCurrency} for this user, i.e. the inverse of {@link #factorToBase}.
     * Returns null when no usable rate exists (so callers can omit the currency
     * rather than show a fabricated value).
     */
    public BigDecimal factorFromBase(String toCurrency, Long userId) {
        String to = toCurrency == null ? "" : toCurrency.trim().toUpperCase();
        if (to.isEmpty() || BASE_CURRENCY.equals(to)) {
            return BigDecimal.ONE;
        }
        List<CurrencyRate> rates = currencyRateRepository.findByUserId(userId);

        // direct: SGD -> to
        BigDecimal direct = latestRate(rates, BASE_CURRENCY, to);
        if (direct != null && direct.compareTo(BigDecimal.ZERO) > 0) {
            return direct;
        }
        // inverse: to -> SGD  =>  1 / rate
        BigDecimal inverse = latestRate(rates, to, BASE_CURRENCY);
        if (inverse != null && inverse.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ONE.divide(inverse, 10, RoundingMode.HALF_UP);
        }
        return null;
    }

    private BigDecimal latestRate(List<CurrencyRate> rates, String from, String to) {
        return rates.stream()
                .filter(r -> from.equalsIgnoreCase(nz(r.getFromCurrency())) && to.equalsIgnoreCase(nz(r.getToCurrency())))
                .max(Comparator.comparing(CurrencyRate::getEffectiveDate,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(CurrencyRate::getRate)
                .orElse(null);
    }

    private String nz(String s) { return s == null ? "" : s.trim(); }
}
