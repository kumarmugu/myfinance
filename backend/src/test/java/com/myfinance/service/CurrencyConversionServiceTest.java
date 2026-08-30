package com.myfinance.service;

import com.myfinance.model.AppUser;
import com.myfinance.model.CurrencyRate;
import com.myfinance.repository.AppUserRepository;
import com.myfinance.repository.CurrencyRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    @Mock private CurrencyRateRepository currencyRateRepository;
    @Mock private AppUserRepository appUserRepository;
    @InjectMocks private CurrencyConversionService service;

    @BeforeEach
    void setUp() {
        // User 1 has no configured base currency, so the service defaults to SGD.
        lenient().when(appUserRepository.findById(1L))
                .thenReturn(Optional.of(AppUser.builder().id(1L).baseCurrency(null).build()));
    }

    private CurrencyRate rate(String from, String to, String value, LocalDate date) {
        return CurrencyRate.builder()
                .fromCurrency(from).toCurrency(to)
                .rate(new BigDecimal(value)).effectiveDate(date)
                .userId(1L).build();
    }

    @Test
    void baseCurrencyConvertsAsIdentity() {
        // SGD -> SGD is always 1, no rate lookup needed
        assertThat(service.factorToBase("SGD", 1L)).isEqualByComparingTo("1");
        assertThat(service.toBase(new BigDecimal("1000"), "SGD", 1L)).isEqualByComparingTo("1000");
    }

    @Test
    void nullOrBlankCurrencyTreatedAsBase() {
        assertThat(service.factorToBase(null, 1L)).isEqualByComparingTo("1");
        assertThat(service.toBase(new BigDecimal("500"), "", 1L)).isEqualByComparingTo("500");
        assertThat(service.toBase(null, "USD", 1L)).isEqualByComparingTo("0");
    }

    @Test
    void usesDirectRateToBase() {
        // USD -> SGD = 1.35
        when(currencyRateRepository.findByUserId(1L))
                .thenReturn(List.of(rate("USD", "SGD", "1.35", LocalDate.of(2026, 1, 1))));

        assertThat(service.factorToBase("USD", 1L)).isEqualByComparingTo("1.35");
        assertThat(service.toBase(new BigDecimal("100"), "USD", 1L)).isEqualByComparingTo("135.00");
    }

    @Test
    void usesInverseRateWhenOnlyReverseExists() {
        // Only SGD -> USD = 0.74 exists; USD -> SGD must be 1/0.74
        when(currencyRateRepository.findByUserId(1L))
                .thenReturn(List.of(rate("SGD", "USD", "0.74", LocalDate.of(2026, 1, 1))));

        BigDecimal factor = service.factorToBase("USD", 1L);
        assertThat(factor).isEqualByComparingTo(new BigDecimal("1").divide(new BigDecimal("0.74"), 10, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void picksLatestRateByEffectiveDate() {
        when(currencyRateRepository.findByUserId(1L)).thenReturn(List.of(
                rate("USD", "SGD", "1.30", LocalDate.of(2025, 1, 1)),
                rate("USD", "SGD", "1.40", LocalDate.of(2026, 6, 1))));

        assertThat(service.factorToBase("USD", 1L)).isEqualByComparingTo("1.40");
    }

    @Test
    void fallsBackToOneWhenNoRate() {
        when(currencyRateRepository.findByUserId(1L)).thenReturn(List.of());
        // No rate configured -> factor 1 (summed as-is, never throws)
        assertThat(service.factorToBase("LKR", 1L)).isEqualByComparingTo("1");
        assertThat(service.toBase(new BigDecimal("2000"), "LKR", 1L)).isEqualByComparingTo("2000");
    }

    @Test
    void factorFromBaseUsesDirectSgdToTarget() {
        when(currencyRateRepository.findByUserId(1L))
                .thenReturn(List.of(rate("SGD", "USD", "0.74", LocalDate.of(2026, 1, 1))));

        assertThat(service.factorFromBase("USD", 1L)).isEqualByComparingTo("0.74");
    }

    @Test
    void factorFromBaseUsesInverseWhenOnlyTargetToSgdExists() {
        when(currencyRateRepository.findByUserId(1L))
                .thenReturn(List.of(rate("USD", "SGD", "1.25", LocalDate.of(2026, 1, 1))));

        // SGD -> USD = 1 / 1.25 = 0.8
        assertThat(service.factorFromBase("USD", 1L)).isEqualByComparingTo(new BigDecimal("0.8"));
    }

    @Test
    void factorFromBaseReturnsNullWhenNoRate() {
        when(currencyRateRepository.findByUserId(1L)).thenReturn(List.of());
        assertThat(service.factorFromBase("USD", 1L)).isNull();
    }

    @Test
    void factorFromBaseIsOneForBaseCurrency() {
        assertThat(service.factorFromBase("SGD", 1L)).isEqualByComparingTo("1");
    }
}
