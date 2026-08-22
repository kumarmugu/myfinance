package com.myfinance.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "currency_rates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CurrencyRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;

    @Column(nullable = false, length = 10)
    private String fromCurrency;

    @Column(nullable = false, length = 10)
    private String toCurrency;

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(nullable = false)
    private LocalDate effectiveDate;

    private LocalDateTime updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}
