package com.myfinance.service;

import com.myfinance.model.AppUser;
import com.myfinance.model.CurrencyRate;
import com.myfinance.repository.AppUserRepository;
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
 * {@link CurrencyRate} entries.
 *
 * The base/reporting currency is configurable per user ({@code AppUser.baseCurrency});
 * when unset it defaults to {@link #DEFAULT_BASE_CURRENCY} (SGD). Original per-record
 * currencies and amounts are always preserved — this service only derives values for
 * consolidation (Net Worth) and display; it never mutates stored data.
 *
 * Resolution order for a factor {@code from -> base}:
 *   1. identity when {@code from} already equals the user's base
 *   2. a direct rate {@code from -> base} (latest by effectiveDate)
 *   3. the inverse of a {@code base -> from} rate (latest by effectiveDate)
 *   4. fallback of 1.0 (amount summed as-is) with a warning — never throws
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrencyConversionService {

    public static final String DEFAULT_BASE_CURRENCY = "SGD";

    /** @deprecated use {@link #getBaseCurrency(Long)}; kept for callers wanting the default. */
    @Deprecated
    public static final String BASE_CURRENCY = DEFAULT_BASE_CURRENCY;

    private final CurrencyRateRepository currencyRateRepository;
    private final AppUserRepository appUserRepository;

    public static final String DEFAULT_DISPLAY_CURRENCIES = "SGD,USD";

    /** The user's configured base/reporting currency, or the default (SGD) when unset. */
    public String getBaseCurrency(Long userId) {
        if (userId == null) return DEFAULT_BASE_CURRENCY;
        return appUserRepository.findById(userId)
                .map(AppUser::getBaseCurrency)
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase())
                .orElse(DEFAULT_BASE_CURRENCY);
    }

    /**
     * The ordered list of display-currency codes offered to the user in the UI toggle.
     * Defaults to {@code SGD,USD} when unset. The user's base currency is always
     * included and placed first so display can always fall back to it.
     */
    public java.util.List<String> getDisplayCurrencies(Long userId) {
        String base = getBaseCurrency(userId);
        String csv = userId == null ? null : appUserRepository.findById(userId)
                .map(AppUser::getDisplayCurrencies).orElse(null);
        if (csv == null || csv.isBlank()) csv = DEFAULT_DISPLAY_CURRENCIES;

        java.util.LinkedHashSet<String> ordered = new java.util.LinkedHashSet<>();
        ordered.add(base); // base always available and first
        for (String c : csv.split(",")) {
            String code = norm(c);
            if (!code.isEmpty()) ordered.add(code);
        }
        return new java.util.ArrayList<>(ordered);
    }

    /**
     * Convert {@code amount} in {@code fromCurrency} to the user's base currency.
     * Null/blank inputs are treated as zero / base currency.
     */
    public BigDecimal toBase(BigDecimal amount, String fromCurrency, Long userId) {
        if (amount == null) return BigDecimal.ZERO;
        return amount.multiply(factorToBase(fromCurrency, userId));
    }

    /**
     * The multiplier that converts one unit of {@code fromCurrency} into the user's
     * base currency. Returns 1 when the currency is the base or no rate is configured.
     */
    public BigDecimal factorToBase(String fromCurrency, Long userId) {
        String base = getBaseCurrency(userId);
        String from = norm(fromCurrency);
        if (from.isEmpty() || base.equals(from)) {
            return BigDecimal.ONE;
        }

        List<CurrencyRate> rates = currencyRateRepository.findByUserId(userId);

        BigDecimal direct = latestRate(rates, from, base);
        if (direct != null && direct.compareTo(BigDecimal.ZERO) > 0) {
            return direct;
        }
        BigDecimal inverse = latestRate(rates, base, from);
        if (inverse != null && inverse.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.ONE.divide(inverse, 10, RoundingMode.HALF_UP);
        }

        log.warn("No FX rate {}->{} (or inverse) for userId={}; summing amount without conversion",
                from, base, userId);
        return BigDecimal.ONE;
    }

    /**
     * The multiplier that converts one unit of the user's base currency into
     * {@code toCurrency}, i.e. the inverse of {@link #factorToBase}. Returns null
     * when no usable rate exists (so callers can omit the currency rather than
     * show a fabricated value). Returns 1 when {@code toCurrency} is the base.
     */
    public BigDecimal factorFromBase(String toCurrency, Long userId) {
        String base = getBaseCurrency(userId);
        String to = norm(toCurrency);
        if (to.isEmpty() || base.equals(to)) {
            return BigDecimal.ONE;
        }
        List<CurrencyRate> rates = currencyRateRepository.findByUserId(userId);

        BigDecimal direct = latestRate(rates, base, to);
        if (direct != null && direct.compareTo(BigDecimal.ZERO) > 0) {
            return direct;
        }
        BigDecimal inverse = latestRate(rates, to, base);
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

    private String norm(String s) { return s == null ? "" : s.trim().toUpperCase(); }
    private String nz(String s) { return s == null ? "" : s.trim(); }
}
